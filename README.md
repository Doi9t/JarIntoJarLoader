##JarIntoJar
- - -

JarIntoJar is a [classloader](https://en.wikipedia.org/wiki/Java_Classloader) that allow to include dependencies as a JAR file (within the main JAR)


**Warning**
This classloader is still in development AND WILL slowdown your application (Depends on how many libraries you have in your application)

Example:
141 MB of libraries, it take about 43.166 seconds to init.
32.8 MB of libraries, it take about 14.119 seconds to init.
2.20 MB of libraries, it take about 2.047 seconds to init.

Please, keep this in mind before using it

**How to use**

1) Include the JarIntoJarLauncher file (JarIntoJarLauncher.java) into your project

2) Edit the jar manifest

With maven (example):
~~~xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-dependency-plugin</artifactId>
            <executions>
                <execution>
                    <id>copy-dependencies</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>copy-dependencies</goal>
                    </goals>
                    <configuration>
                        <outputDirectory>${project.build.directory}/classes/lib</outputDirectory>
                        <overWriteReleases>false</overWriteReleases>
                        <overWriteSnapshots>false</overWriteSnapshots>
                        <overWriteIfNewer>true</overWriteIfNewer>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <configuration>
                <archive>
                    <manifest>
                        <mainClass>org.jarintojar.JarIntoJarLauncher</mainClass> <!--Point to JarIntoJarLauncher -->
                        <addClasspath>true</addClasspath>
                    </manifest>
                    <manifestEntries>
                        <Class-Path>lib/</Class-Path> <!--You can change the location of the libs -->
                        <Entry-Point>org.app.MyMain</Entry-Point> <!--Change this to your main class -->
                    </manifestEntries>
                </archive>
            </configuration>
        </plugin>
    </plugins>
</build>
~~~

Manually:
~~~
    Edit the jar manifest
        - Add the value "Entry-Point: [Location of your main class]"
        - Edit the value "Main-Class: [Location of JarIntoJarLauncher]"
~~~