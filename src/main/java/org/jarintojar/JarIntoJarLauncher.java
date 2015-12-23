/*
 *    Copyright 2014 - 2015 Yannick Watier
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.jarintojar;

import org.jarintojar.loader.JarIntoJarLoader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.jar.Manifest;

/**
 * Created by Yannick on 12/21/2015.
 */
public class JarIntoJarLauncher {
    public static void main(String[] args) {
        JarIntoJarLoader loader = new JarIntoJarLoader();

        try {
            //Read the main from the manifest
            URLClassLoader urlCl = (URLClassLoader) JarIntoJarLauncher.class.getClassLoader();
            Manifest manifest = new Manifest(urlCl.findResource("META-INF/MANIFEST.MF").openStream());
            String binaryNameMain = manifest.getMainAttributes().getValue("Entry-Point");

            if (binaryNameMain != null && !binaryNameMain.isEmpty()) {

                Class<?> main = loader.loadClass(binaryNameMain);

                Method mainMethod = main.getMethod("main", String[].class);

                String[] params = null;
                mainMethod.invoke(null, (Object) params);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
