package de.ohmesoftware.javadoctoopenapischema.model.subdir;

/**
 * A user being able to log-in.
 * <p>
 *     More details.
 * </p>
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net">Karsten Ohme
 * (k_o_@users.sourceforge.net)</a>
 */
public class User {

    /**
     * The username.
     */
    public String username;
    /**
     * The email address.
     * <p>
     *     Escape "test"
     * </p>
     */
    @io.swagger.v3.oas.annotations.media.Schema(description = "Test")
    public String emailAddress;
    /**
     * The password.
     */
    public String password;
    /**
     * The role.
     */
    public String role;
    public String firstName;
    public String lastName;
    public String extra;
}
