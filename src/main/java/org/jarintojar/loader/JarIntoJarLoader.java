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

package org.jarintojar.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

/**
 * Created by Yannick on 12/21/2015.
 */
public class JarIntoJarLoader extends ClassLoader {

    private final Map<String, byte[]> rawClassChache;

    public JarIntoJarLoader() {
        rawClassChache = new HashMap<String, byte[]>();

        try {
            Instant start = Instant.now();
            initClassCache(new JarInputStream(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL().openStream()));
            Instant end = Instant.now();
            System.out.println(Duration.between(start, end));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }

    //Entry points
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        Class<?> loadedClass = findLoadedClass(name);

        if (loadedClass == null) { //Try in the parent classloader (remove the ClassNotFoundException on the Object class)
            try {
                loadedClass = getParent().loadClass(name);
            } catch (ClassNotFoundException ex) {
            }
        }

        return (loadedClass != null) ? loadedClass : getClassFromCache(name);
    }

    private Class<?> getClassFromCache(String name) throws ClassNotFoundException {
        byte[] bytes = rawClassChache.get(name);

        if (bytes == null) {
            throw new ClassNotFoundException("The class is not present in the cache !");
        }

        return defineClass(name, bytes, 0, bytes.length);
    }

    private void initClassCache(JarInputStream zis) throws IOException {
        if (zis != null) {
            JarEntry ze = zis.getNextJarEntry();

            while (ze != null) {
                if (!ze.isDirectory()) {

                    String baseName = ze.getName();
                    String fileExt = getFilenamePart(baseName, FilenamePart.FILE_EXT);

                    if (fileExt != null && !fileExt.isEmpty()) {
                        //Check the 4 first bytes
                        byte[] currentBytes = new byte[4];
                        zis.read(currentBytes, 0, 4);

                        switch (getFileType(currentBytes, fileExt)) {
                            case CLASS:
                                String binaryName = getBinaryName(baseName);
                                System.out.println("Found class: " + binaryName);
                                rawClassChache.put(binaryName, concatArrays(currentBytes, readInput(zis)));
                                break;
                            case JAR:
                                System.out.println("Found jar: " + baseName);
                                initClassCache(new JarInputStream(new ByteArrayInputStream(concatArrays(currentBytes, readInput(zis)))));
                                break;
                        }
                    }
                }
                ze = zis.getNextJarEntry();
            }
        }
    }


    private byte[] readInput(InputStream stream) throws IOException {
        ByteArrayOutputStream binDataBaos = new ByteArrayOutputStream();

        for (int c; (c = stream.read()) != -1; ) {
            binDataBaos.write((byte) c);
        }

        return binDataBaos.toByteArray();
    }


    private String getFilename(String path, boolean showExt) {

        String value = null;

        if (path != null && !path.isEmpty()) {

            String[] split = path.split(Pattern.quote("/"));

            if (split.length > 0) {

                value = split[split.length - 1];

                if (!showExt) {
                    value = value.split(Pattern.quote("."))[0];
                }
            }
        }

        return value;
    }

    private String getFilenamePart(String filename, FilenamePart part) {

        String value = null;

        if (filename != null && !filename.isEmpty() && part != null) {

            String filenameWithExt = getFilename(filename, true);

            if (filenameWithExt != null && !filenameWithExt.isEmpty()) {
                String[] split = filenameWithExt.split(Pattern.quote("."));

                if (split.length > 0) {

                    switch (part) {
                        case FILE_EXT:
                            value = split[split.length - 1];
                            break;
                        case FILENAME:
                            value = split[split.length - 2];
                            break;
                    }
                }
            }
        }

        return value;
    }

    private String getBinaryName(String path) {

        StringBuffer value = new StringBuffer();
        String filename = "";

        if (path != null && !path.isEmpty()) {
            List<String> list = new ArrayList<String>();
            list.addAll(Arrays.asList(path.split("/")));
            filename = getFilenamePart(list.remove(list.size() - 1), FilenamePart.FILENAME);

            for (String s : list) {
                value.append(s).append(".");
            }
        }

        return value.toString() + filename;
    }


    private static byte[] concatArrays(byte[]... arrs) {

        byte[] value = null;

        if (arrs != null && arrs.length > 0) {
            int len = 0, idx = 0;

            for (byte[] arr : arrs) { //Get the length of the final array
                if (arr != null && arr.length > 0) {
                    len += arr.length;
                }
            }

            value = new byte[len];

            for (byte[] arr : arrs) { //Put the values into the array

                if (arr != null && arr.length > 0) {
                    for (byte b : arr) {
                        value[idx] = b;
                        idx++;
                    }
                }
            }
        }

        return value;
    }

    private FileTypeEnum getFileType(byte[] bytes, String fileExt) {

        FileTypeEnum value = FileTypeEnum.UNKNOWN;

        if (bytes != null && bytes.length == 4
                && fileExt != null && !fileExt.isEmpty()) {

            for (FileTypeEnum fileTypeEnum : FileTypeEnum.values()) {
                if (fileTypeEnum != null && Arrays.equals(bytes, fileTypeEnum.getHeader()) && fileExt.equals(fileTypeEnum.getFileExt())) {
                    value = fileTypeEnum;
                }
            }
        }

        return value;
    }

    private enum FileTypeEnum {
        JAR("application/java-archive", "jar", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}),
        WAR("application/x-java-war", "war", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}),
        CLASS("application/java-vm", "class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}),
        UNKNOWN("application/x-unknown", null, null);

        private String mimeType, fileExt;
        private byte[] header;

        FileTypeEnum(String mimeType, String fileExt, byte[] header) {
            this.mimeType = mimeType;
            this.fileExt = fileExt;
            this.header = (header != null) ? header.clone() : null;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getFileExt() {
            return fileExt;
        }

        public byte[] getHeader() {
            return (header != null) ? header.clone() : null;
        }
    }

    private enum FilenamePart {
        FILENAME, FILE_EXT
    }
}