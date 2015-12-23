package org.jarintojar;

import org.jarintojar.loader.JarIntoJarLoader;

/**
 * Created by Yannick on 12/21/2015.
 */
public class App {
    public static void main(String[] args) {
        JarIntoJarLoader loader = new JarIntoJarLoader();

        try {
            Class<?> aClass = loader.loadClass("org.springframework.security.crypto.keygen.SharedKeyGenerator");

            System.out.println(aClass);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }
}
