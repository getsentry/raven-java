package io.sentry.jmx;

import io.sentry.SentryClient;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

public abstract class AbstractSentryConfiguration implements SentryConfigurationMXBean {
  protected abstract SentryClient getClient();

  @Override
  public String getRelease() {
    return getClient().getRelease();
  }

  @Override
  public void setRelease(String release) {
    getClient().setRelease(release);
  }

  @Override
  public String getDist() {
    return getClient().getDist();
  }

  @Override
  public void setDist(String dist) {
    getClient().setDist(dist);
  }

  @Override
  public String getServerName() {
    return getClient().getServerName();
  }

  @Override
  public void setServerName(String serverName) {
    getClient().setServerName(serverName);
  }

  public ObjectName register(MBeanServer server, String name)
      throws MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException, InstanceAlreadyExistsException {
    return server.registerMBean(new StandardMBean(this, SentryConfigurationMXBean.class, true), makeName(name)).getObjectName();
  }

  protected static ObjectName makeName(String instance) throws MalformedObjectNameException {
    return new ObjectName("io.sentry:type=SentryConfiguration,name=" + instance);
  }
}
