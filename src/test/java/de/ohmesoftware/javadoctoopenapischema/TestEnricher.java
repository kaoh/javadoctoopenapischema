package de.ohmesoftware.javadoctoopenapischema;

import de.ohmesoftware.javadoctoopenapischema.model.subdir.User;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;

import static junit.framework.TestCase.*;

/**
 * Test.
 *
 * @author <a href="mailto:k_o_@users.sourceforge.net">Karsten Ohme
 * (k_o_@users.sourceforge.net)</a>
 */
public class TestEnricher {

    private static String buildPath(String classOrPackageName) {
        return "src/test/java/"+ classOrPackageName.replace(".", "/");
    }

    @After
    public void after() throws Exception {
        FileUtils.copyFile(new File(buildPath(User.class.getName())+".bak"),
                new File(buildPath(User.class.getName())+".java"));
    }

    @Test
    public void enrich() throws Exception {
        Enricher enricher = new Enricher(buildPath(User.class.getPackage().getName().substring(0,
                User.class.getPackage().getName().lastIndexOf("."))),
                Collections.singleton("**User.java"), Collections.singleton("**.bak"));
        enricher.enrich();
        String newContent = IOUtils.toString(new FileReader(new File(buildPath(User.class.getName())+".java")));
        assertTrue(newContent.contains("package de.ohmesoftware.javadoctoopenapischema.model.subdir;"));
        assertTrue(newContent.contains("@io.swagger.v3.oas.annotations.media.Schema(description = \"Escape \\\"test\\\"\", title = \"The email address.\")"));
        assertFalse(newContent.contains("@io.swagger.v3.oas.annotations.media.Schema()"));
    }
}
