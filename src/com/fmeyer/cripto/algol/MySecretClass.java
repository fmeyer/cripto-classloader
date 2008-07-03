package com.fmeyer.cripto.algol;

import java.util.Random;

/**
 * Created by IntelliJ IDEA.
 * User: Fernando
 * Date: Jul 31, 2006
 * Time: 12:18:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class MySecretClass {
    public static int mySecretAlgorithm ()
    {
        return (int) s_random.nextInt ();
    }
    private static final Random s_random = new Random (System.currentTimeMillis ());

}
