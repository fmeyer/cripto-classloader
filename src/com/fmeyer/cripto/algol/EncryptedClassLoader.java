package com.fmeyer.cripto.algol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

// ----------------------------------------------------------------------------
/**
 * This class doubles up as a simple binary file "encryptor" and a custom
 * ClassLoader that will reverse the encryption during actual class loading.<P>
 *
 * Usage:
 * <PRE>
 *  java -encrypt <dir with classes to be encrypted> <full class name1> <full class name1> ...
 * or
 *  java -run <classpath dir> <main class> ...app args...
 * </PRE>
 * where the directory that follows both <code>-encrypt</code> and <code>-run</code>
 * should be the one into which you have compiled the original classes.
 *
 * @author (C) <a href="http://www.javaworld.com/columns/jw-qna-index.shtml">Vlad Roubtsov</a>, 2003
 */
public class EncryptedClassLoader extends URLClassLoader
{
    // public: ................................................................

    public static final String USAGE = "usage: EncryptedClassLoader " +
        "(" +
        "-run <encrypt dir> <app_main_class> <app_main_args...>" +
        " | " +
        "-encrypt <classpath dir> <class 1> <class 2> ..." +
        ")";

    public static boolean TRACE = true; // 'true' causes some extra logging

    /**
     * See usage details in the class javadoc.
     */
    public static void main (final String [] args)
        throws Exception
    {
        if ( args.length == 1)
            throw new IllegalArgumentException(USAGE);


        if ("-run".equals (args [0]) && (args.length >=  3))
        {
            // create a custom loader that will use the current loader as
            // delegation parent:
            final ClassLoader appLoader =
                new EncryptedClassLoader (EncryptedClassLoader.class.getClassLoader (),
                new File (args [1]));

            // Thread context loader must be adjusted as well:
            Thread.currentThread ().setContextClassLoader (appLoader);

            final Class app = appLoader.loadClass (args [2]);

            final Method appmain = app.getMethod ("main", String [].class);
            final String [] appargs = new String [args.length - 3];
            System.arraycopy (args, 3, appargs, 0, appargs.length);

            appmain.invoke (null, new Object [] {appargs});
        }
        else if ("-encrypt".equals (args [0]) && (args.length >= 3))
        {
            final File srcDir = new File (args [1]);

            for (int f = 2; f < args.length; ++ f)
            {
                final File file = new File (args [f].replace ('.', File.separatorChar) + ".class");
                final byte [] classBytes;

                InputStream in = null;
                final File srcFile = new File (srcDir, file.getPath ());
                try
                {
                    srcFile.getParentFile ().mkdirs ();

                    in = new FileInputStream (srcFile);

                    classBytes = readFully (in);
                    // "encrypt":
                    crypt (classBytes);
                }
                finally
                {
                    if (in != null) try { in.close (); } catch (Exception ignore) {}
                }

                OutputStream out = null;
                try
                {
                    final File destFile = new File (srcDir, file.getPath ());
                    destFile.getParentFile ().mkdirs ();

                    out = new FileOutputStream (destFile);
                     // overwrite the original file:
                    out.write (classBytes);
                }
                finally
                {
                    if (out != null) try { out.close (); } catch (Exception ignore) {}
                }

                if (TRACE) System.out.println ("encrypted [" + file + "]");
            }
        }
        else
            throw new IllegalArgumentException (USAGE);
    }

    /**
     * <B>DO NOT USE IN PRODUCTION CODE!</B> Proper classloading is tricky and
     * this implementation omits many important details.<P>
     *
     * Overrides java.lang.ClassLoader.loadClass() to change the usual parent-child
     * delegation rules just enough to be able to "snatch" application classes
     * from under system classloader's nose.
     */
    public Class loadClass (final String name, final boolean resolve)
        throws ClassNotFoundException
    {
        if (TRACE) System.out.println ("loadClass (" + name + ", " + resolve + ")");

        Class c = null;

        // first, check if this class has already been defined by this classloader
        // instance:
        c = findLoadedClass (name);

        if (c == null)
        {
            Class parentsVersion = null;
            try
            {
                // this is slightly unorthodox: do a trial load via the
                // parent loader and note whether the parent delegated or not;
                // what this accomplishes is proper delegation for all core
                // and extension classes without my having to filter on class name:
                parentsVersion = getParent ().loadClass (name);

                if (parentsVersion.getClassLoader () != getParent ())
                    c = parentsVersion;
            }
            catch (ClassNotFoundException ignore) {}
            catch (ClassFormatError ignore) {}

            if (c == null)
            {
                try
                {
                    // ok, either 'c' was loaded by the system (not the bootstrap
                    // or extension) loader (in which case I want to ignore that
                    // definition) or the parent failed altogether; either way I
                    // attempt to define my own version:
                    c = findClass (name);
                }
                catch (ClassNotFoundException ignore)
                {
                    // if that failed, fall back on the parent's version
                    // [which could be null at this point]:
                    c = parentsVersion;
                }
            }
        }

        if (c == null)
            throw new ClassNotFoundException (name);

        if (resolve)
            resolveClass (c);

        return c;
    }

    // protected: .............................................................


    /**
     * <B>DO NOT USE IN PRODUCTION CODE!</B> Proper classloading is tricky and
     * this implementation omits many important details.<P>
     *
     * Overrides java.new.URLClassLoader.defineClass() to be able to call
     * crypt() before defining a class.
     */
    protected Class findClass (final String name) throws ClassNotFoundException
    {
        if (TRACE) System.out.println ("findClass (" + name + ")");

        // .class files are not guaranteed to be loadable as resources;
        // but if Sun's code does it, so perhaps can mine...
        final String classResource = name.replace ('.', '/') + ".class";
        final URL classURL = getResource (classResource);

        if (classURL == null)
            throw new ClassNotFoundException (name);
        else
        {
            InputStream in = null;
            try
            {
                in = classURL.openStream ();

                final byte [] classBytes = readFully (in);

                // "decrypt":
                crypt (classBytes);
                if (TRACE) System.out.println ("decrypted [" + name + "]");

                return defineClass (name, classBytes, 0, classBytes.length);
            }
            catch (IOException ioe)
            {
                throw new ClassNotFoundException (name);
            }
            finally
            {
                if (in != null) try { in.close (); } catch (Exception ignore) {}
            }
        }
    }

    // package: ...............................................................

    // private: ...............................................................


    /**
     * This classloader is only capable of custom loading from a single directory.
     */
    private EncryptedClassLoader (final ClassLoader parent, final File classpath)
        throws MalformedURLException
    {
        super (new URL [] {classpath.toURL ()}, parent);

        if (parent == null)
            throw new IllegalArgumentException ("EncryptedClassLoader" +
                " requires a non-null delegation parent");
    }

    /**
     * De/encrypts binary data in a given byte array. Calling the method again
     * reverses the encryption.
     */
    private static void crypt (final byte [] data)
    {
        for (int i = 8; i < data.length; ++ i) data [i] ^= 0x5A;
    }

    /**
     * Reads the entire contents of a given stream into a flat byte array.
     */
    private static byte [] readFully (final InputStream in)
        throws IOException
    {
        final ByteArrayOutputStream buf1 = new ByteArrayOutputStream ();
        final byte [] buf2 = new byte [8 * 1024];

        for (int read; (read = in.read (buf2)) > 0; )
        {
            buf1.write (buf2, 0, read);
        }

        return buf1.toByteArray ();
    }

} // end of class
// ----------------------------------------------------------------------------