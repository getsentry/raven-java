package io.sentry;

import io.sentry.protocol.Contexts;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Scope data to be sent with the event */
public final class Scope implements Cloneable {

  /** Scope's SentryLevel */
  private @Nullable SentryLevel level;

  /** Scope's {@link ITransaction}. */
  private @Nullable ITransaction transaction;

  /** Scope's transaction name. Used when using error reporting without the performance feature. */
  private @Nullable String transactionName;

  /** Scope's user */
  private @Nullable User user;

  /** Scope's request */
  private @Nullable Request request;

  /** Scope's fingerprint */
  private @NotNull List<String> fingerprint = new ArrayList<>();

  /** Scope's breadcrumb queue */
  private @NotNull Queue<Breadcrumb> breadcrumbs;

  /** Scope's tags */
  private @NotNull Map<String, String> tags = new ConcurrentHashMap<>();

  /** Scope's extras */
  private @NotNull Map<String, Object> extra = new ConcurrentHashMap<>();

  /** Scope's event processor list */
  private @NotNull List<EventProcessor> eventProcessors = new CopyOnWriteArrayList<>();

  /** Scope's SentryOptions */
  private final @NotNull SentryOptions options;

  // TODO Consider: Scope clone doesn't clone sessions

  /** Scope's current session */
  private volatile @Nullable Session session;

  /** Session lock, Ops should be atomic */
  private final @NotNull Object sessionLock = new Object();

  /** Scope's contexts */
  private @NotNull Contexts contexts = new Contexts();

  /** Scope's attachments */
  private @NotNull List<Attachment> attachments = new CopyOnWriteArrayList<>();

  /**
   * Scope's ctor
   *
   * @param options the options
   */
  public Scope(final @NotNull SentryOptions options) {
    this.options = options;
    this.breadcrumbs = createBreadcrumbsList(options.getMaxBreadcrumbs());
  }

  /**
   * Returns the Scope's SentryLevel
   *
   * @return the SentryLevel
   */
  public @Nullable SentryLevel getLevel() {
    return level;
  }

  /**
   * Sets the Scope's SentryLevel Level from scope exceptionally take precedence over the event
   *
   * @param level the SentryLevel
   */
  public void setLevel(final @Nullable SentryLevel level) {
    this.level = level;
  }

  /**
   * Returns the Scope's transaction name.
   *
   * @return the transaction
   */
  public @Nullable String getTransactionName() {
    final ITransaction tx = this.transaction;
    return tx != null ? tx.getTransaction() : transactionName;
  }

  /**
   * Sets the Scope's transaction.
   *
   * @param transaction the transaction
   */
  public void setTransaction(final @NotNull String transaction) {
    final ITransaction tx = this.transaction;
    if (tx != null) {
      tx.setName(transaction);
    }
    this.transactionName = transaction;
  }

  /**
   * Returns current active Span or Transaction.
   *
   * @return current active Span or Transaction or null if transaction has not been set.
   */
  @Nullable
  public ISpan getSpan() {
    final ITransaction tx = transaction;
    if (tx != null) {
      final Span span = tx.getLatestActiveSpan();

      if (span != null) {
        return span;
      }
    }
    return tx;
  }

  /**
   * Sets the current active transaction
   *
   * @param transaction the transaction
   */
  public void setTransaction(final @NotNull ITransaction transaction) {
    this.transaction = Objects.requireNonNull(transaction, "transaction is required");

    // TODO: double-check this
    // setTransaction(String) replaces the transaction.name so I guess this makes sense too
    transactionName = this.transaction.getName();
  }

  /**
   * Returns the Scope's user
   *
   * @return the user
   */
  public @Nullable User getUser() {
    return user;
  }

  /**
   * Sets the Scope's user
   *
   * @param user the user
   */
  public void setUser(final @Nullable User user) {
    this.user = user;

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setUser(user);
      }
    }
  }

  /**
   * Returns the Scope's request
   *
   * @return the request
   */
  public @Nullable Request getRequest() {
    return request;
  }

  /**
   * Sets the Scope's request
   *
   * @param request the request
   */
  public void setRequest(final @Nullable Request request) {
    this.request = request;
  }

  /**
   * Returns the Scope's fingerprint list
   *
   * @return the fingerprint list
   */
  @NotNull
  List<String> getFingerprint() {
    return fingerprint;
  }

  /**
   * Sets the Scope's fingerprint list
   *
   * @param fingerprint the fingerprint list
   */
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (fingerprint == null) {
      return;
    }
    this.fingerprint = fingerprint;
  }

  /**
   * Returns the Scope's breadcrumbs queue
   *
   * @return the breadcrumbs queue
   */
  @NotNull
  Queue<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  /**
   * Executes the BeforeBreadcrumb callback
   *
   * @param callback the BeforeBreadcrumb callback
   * @param breadcrumb the breadcrumb
   * @param hint the hint
   * @return the mutated breadcrumb or null if dropped
   */
  private @Nullable Breadcrumb executeBeforeBreadcrumb(
      final @NotNull SentryOptions.BeforeBreadcrumbCallback callback,
      @NotNull Breadcrumb breadcrumb,
      final @Nullable Object hint) {
    try {
      breadcrumb = callback.execute(breadcrumb, hint);
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "The BeforeBreadcrumbCallback callback threw an exception. It will be added as breadcrumb and continue.",
              e);

      breadcrumb.setData("sentry:message", e.getMessage());
    }
    return breadcrumb;
  }

  /**
   * Adds a breadcrumb to the breadcrumbs queue It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   * @param hint the hint
   */
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, final @Nullable Object hint) {
    if (breadcrumb == null) {
      return;
    }

    SentryOptions.BeforeBreadcrumbCallback callback = options.getBeforeBreadcrumb();
    if (callback != null) {
      breadcrumb = executeBeforeBreadcrumb(callback, breadcrumb, hint);
    }
    if (breadcrumb != null) {
      this.breadcrumbs.add(breadcrumb);

      if (options.isEnableScopeSync()) {
        for (final IScopeObserver observer : options.getScopeObservers()) {
          observer.addBreadcrumb(breadcrumb);
        }
      }
    } else {
      options.getLogger().log(SentryLevel.INFO, "Breadcrumb was dropped by beforeBreadcrumb");
    }
  }

  /**
   * Adds a breadcrumb to the breadcrumbs queue It also executes the BeforeBreadcrumb callback if
   * set
   *
   * @param breadcrumb the breadcrumb
   */
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  /** Clear all the breadcrumbs */
  public void clearBreadcrumbs() {
    breadcrumbs.clear();
  }

  /** Clears the transaction. */
  public void clearTransaction() {
    transaction = null;
    transactionName = null;
  }

  /**
   * Returns active transaction or null if there is no active transaction.
   *
   * @return the transaction
   */
  @Nullable
  public ITransaction getTransaction() {
    return this.transaction;
  }

  /** Resets the Scope to its default state */
  public void clear() {
    level = null;
    transaction = null;
    user = null;
    request = null;
    fingerprint.clear();
    breadcrumbs.clear();
    tags.clear();
    extra.clear();
    eventProcessors.clear();
    clearTransaction();
    attachments.clear();
  }

  /**
   * Returns the Scope's tags
   *
   * @return the tags map
   */
  @NotNull
  Map<String, String> getTags() {
    return tags;
  }

  /**
   * Sets a tag to Scope's tags
   *
   * @param key the key
   * @param value the value
   */
  public void setTag(final @NotNull String key, final @NotNull String value) {
    this.tags.put(key, value);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setTag(key, value);
      }
    }
  }

  /**
   * Removes a tag from the Scope's tags
   *
   * @param key the key
   */
  public void removeTag(final @NotNull String key) {
    this.tags.remove(key);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.removeTag(key);
      }
    }
  }

  /**
   * Returns the Scope's extra map
   *
   * @return the extra map
   */
  @NotNull
  Map<String, Object> getExtras() {
    return extra;
  }

  /**
   * Sets an extra to the Scope's extra map
   *
   * @param key the key
   * @param value the value
   */
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    this.extra.put(key, value);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.setExtra(key, value);
      }
    }
  }

  /**
   * Removes an extra from the Scope's extras
   *
   * @param key the key
   */
  public void removeExtra(final @NotNull String key) {
    this.extra.remove(key);

    if (options.isEnableScopeSync()) {
      for (final IScopeObserver observer : options.getScopeObservers()) {
        observer.removeExtra(key);
      }
    }
  }

  /**
   * Returns the Scope's contexts
   *
   * @return the contexts
   */
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Object value) {
    this.contexts.put(key, value);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Boolean value) {
    final Map<String, Boolean> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull String value) {
    final Map<String, String> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Sets the Scope's contexts
   *
   * @param key the context key
   * @param value the context value
   */
  public void setContexts(final @NotNull String key, final @NotNull Number value) {
    final Map<String, Number> map = new HashMap<>();
    map.put("value", value);
    setContexts(key, map);
  }

  /**
   * Removes a value from the Scope's contexts
   *
   * @param key the Key
   */
  public void removeContexts(final @NotNull String key) {
    contexts.remove(key);
  }

  /**
   * Returns the Scopes's attachments
   *
   * @return the attachments
   */
  @NotNull
  List<Attachment> getAttachments() {
    return new CopyOnWriteArrayList<>(attachments);
  }

  /**
   * Adds an attachment to the Scope's list of attachments. The SDK adds the attachment to every
   * event and transaction sent to Sentry.
   *
   * @param attachment The attachment to add to the Scope's list of attachments.
   */
  public void addAttachment(final @NotNull Attachment attachment) {
    attachments.add(attachment);
  }

  /**
   * Creates a breadcrumb list with the max number of breadcrumbs
   *
   * @param maxBreadcrumb the max number of breadcrumbs
   * @return the breadcrumbs queue
   */
  private @NotNull Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb));
  }

  /**
   * Clones a Scope aka deep copy
   *
   * @return the cloned Scope
   * @throws CloneNotSupportedException if object is not cloneable
   */
  @Override
  public @NotNull Scope clone() throws CloneNotSupportedException {
    final Scope clone = (Scope) super.clone();

    final SentryLevel levelRef = level;
    clone.level =
        levelRef != null ? SentryLevel.valueOf(levelRef.name().toUpperCase(Locale.ROOT)) : null;

    final User userRef = user;
    clone.user = userRef != null ? userRef.clone() : null;

    final Request requestRef = request;
    clone.request = requestRef != null ? requestRef.clone() : null;

    clone.fingerprint = new ArrayList<>(fingerprint);
    clone.eventProcessors = new CopyOnWriteArrayList<>(eventProcessors);

    final Queue<Breadcrumb> breadcrumbsRef = breadcrumbs;

    Queue<Breadcrumb> breadcrumbsClone = createBreadcrumbsList(options.getMaxBreadcrumbs());

    for (Breadcrumb item : breadcrumbsRef) {
      final Breadcrumb breadcrumbClone = item.clone();
      breadcrumbsClone.add(breadcrumbClone);
    }
    clone.breadcrumbs = breadcrumbsClone;

    final Map<String, String> tagsRef = tags;

    final Map<String, String> tagsClone = new ConcurrentHashMap<>();

    for (Map.Entry<String, String> item : tagsRef.entrySet()) {
      if (item != null) {
        tagsClone.put(item.getKey(), item.getValue()); // shallow copy
      }
    }

    clone.tags = tagsClone;

    final Map<String, Object> extraRef = extra;

    Map<String, Object> extraClone = new ConcurrentHashMap<>();

    for (Map.Entry<String, Object> item : extraRef.entrySet()) {
      if (item != null) {
        extraClone.put(item.getKey(), item.getValue()); // shallow copy
      }
    }

    clone.extra = extraClone;

    clone.contexts = contexts.clone();

    clone.attachments = new CopyOnWriteArrayList<>(attachments);

    return clone;
  }

  /**
   * Returns the Scope's event processors
   *
   * @return the event processors list
   */
  @NotNull
  List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  /**
   * Adds an event processor to the Scope's event processors list
   *
   * @param eventProcessor the event processor
   */
  public void addEventProcessor(final @NotNull EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }

  /**
   * Callback to do atomic operations on session
   *
   * @param sessionCallback the IWithSession callback
   * @return a clone of the Session after executing the callback and mutating the session
   */
  @Nullable
  Session withSession(final @NotNull IWithSession sessionCallback) {
    Session cloneSession = null;
    synchronized (sessionLock) {
      sessionCallback.accept(session);

      if (session != null) {
        cloneSession = session.clone();
      }
    }
    return cloneSession;
  }

  /** the IWithSession callback */
  interface IWithSession {

    /**
     * The accept method of the callback
     *
     * @param session the current session or null if none exists
     */
    void accept(@Nullable Session session);
  }

  /**
   * Returns a previous session (now closed) bound to this scope together with the newly created one
   *
   * @return the SessionPair with the previous closed session if exists and the current session
   */
  @NotNull
  SessionPair startSession() {
    Session previousSession;
    SessionPair pair;
    synchronized (sessionLock) {
      if (session != null) {
        // Assumes session will NOT flush itself (Not passing any hub to it)
        session.end();
      }
      previousSession = session;

      session =
          new Session(
              options.getDistinctId(), user, options.getEnvironment(), options.getRelease());

      final Session previousClone = previousSession != null ? previousSession.clone() : null;
      pair = new SessionPair(session.clone(), previousClone);
    }
    return pair;
  }

  /** The SessionPair class */
  static final class SessionPair {

    /** the previous session if exists */
    private final @Nullable Session previous;

    /** The current Session */
    private final @NotNull Session current;

    /**
     * The SessionPar ctor
     *
     * @param current the current session
     * @param previous the previous sessions if exists or null
     */
    public SessionPair(final @NotNull Session current, final @Nullable Session previous) {
      this.current = current;
      this.previous = previous;
    }

    /**
     * REturns the previous session
     *
     * @return the previous sessions if exists or null
     */
    public @Nullable Session getPrevious() {
      return previous;
    }

    /**
     * Returns the current session
     *
     * @return the current session
     */
    public @NotNull Session getCurrent() {
      return current;
    }
  }

  /**
   * Ends a session, unbinds it from the scope and returns it.
   *
   * @return the previous session
   */
  @Nullable
  Session endSession() {
    Session previousSession = null;
    synchronized (sessionLock) {
      if (session != null) {
        session.end();
        previousSession = session.clone();
        session = null;
      }
    }
    return previousSession;
  }
}
