package de.ohmesoftware.javadoctoopenapischema.model.subdir;

import javax.persistence.Column;
import javax.persistence.Lob;
import javax.validation.constraints.*;
import java.util.List;

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

    @Column(length = 64, updatable = false)
    @Size(min = 36)
    public String dbId;

    /**
     * The username.
     */
    @NotEmpty
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
    @Column(nullable = false)
    public String password;
    /**
     * The role.
     */
    public String role;
    @Size(min = 2, max = 64)
    public String firstName;
    @NotNull
    public String lastName;
    public String extra;

    @Min(0)
    @Max(value = 10)
    public int loginAttempts;

    @Column(length = 2048)
    @Lob
    @NotEmpty
    public byte[] data;

    @Min(0)
    @Max(100000)
    public int quantity;

    /**
     * A nice foobar.
     * <p>
     *   More detailed description of the foobar.
     * </p>
     * @return the foobar.
     */
    String getFoobar() {
        return null;
    }

    /**
     * All bar.
     * <p>
     *   More detailed description of the list of bars.
     * </p>
     * @return the bars.
     */
    List<Bar> getbars() {
        return null;
    }

}
