package com.getsentry.raven.event;

/**
 * Builder to assist with the creation of {@link User}s.
 */
public class UserBuilder {
    private String id;
    private String username;
    private String ipAddress;
    private String email;

    /**
     * Sets the Id for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder withId(String value) {
        this.id = value;
        return this;
    }

    /**
     * Sets the username for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder withUsername(String value) {
        this.username = value;
        return this;
    }

    /**
     * Sets the ipAddress for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder withIpAddress(String value) {
        this.ipAddress = value;
        return this;
    }

    /**
     * Sets the email for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder withEmail(String value) {
        this.email = value;
        return this;
    }

    /**
     * Build and return the {@link User} object.
     *
     * @return User
     */
    public User build() {
        return new User(id, username, ipAddress, email);
    }
}
