/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.github.dcevm.installer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kerstin Breiteneder
 * @author Christoph Wimberger
 * @author Ivan Dubrov
 * @author Jiri Bubnik
 */
public class Installer {

    private ConfigurationInfo config;

    public Installer(ConfigurationInfo config) {
        this.config = config;
    }

    public void install(String javaVersion, Path dir, boolean bit64, boolean altjvm) throws IOException, DcevmPatchNotFoundException {
        if (config.isJDK(dir)) {
            dir = dir.resolve(config.getJREDirectory());
        }

        if (!altjvm) {
            Path serverPath = dir.resolve(config.getServerPath(bit64));
            if (Files.exists(serverPath)) {
                installClientServer(javaVersion, serverPath, bit64);
            }

            Path clientPath = dir.resolve(config.getClientPath());
            if (Files.exists(clientPath) && !bit64) {
                installClientServer(javaVersion, clientPath, false);
            }
        } else {
            Path altjvmPath = dir.resolve(bit64 ? config.getDcevm64Path() : config.getDcevm32Path());
            if (!Files.exists(altjvmPath)) {
                Files.createDirectory(altjvmPath);
            }
            installClientServer(javaVersion, altjvmPath, bit64);
        }
    }

    /**
     * Try to uninstall DCEVM from all locations (skip if not exists).
     */
    public void uninstall(Path dir, boolean bit64) throws IOException {
        if (config.isJDK(dir)) {
            dir = dir.resolve(config.getJREDirectory());
        }

        Path serverPath = dir.resolve(config.getServerPath(bit64));
        if (Files.exists(serverPath)) {
            uninstallClientServer(serverPath);
        }

        Path clientPath = dir.resolve(config.getClientPath());
        if (Files.exists(clientPath) && !bit64) {
            uninstallClientServer(clientPath);
        }

        Path dcevm32Path = dir.resolve(config.getDcevm32Path());
        if (Files.exists(dcevm32Path)) {
            Files.deleteIfExists(dcevm32Path.resolve(config.getLibraryName()));
            Files.deleteIfExists(dcevm32Path.resolve(config.getBackupLibraryName()));
            Files.delete(dcevm32Path);
        }

        Path dcevm64Path = dir.resolve(config.getDcevm64Path());
        if (Files.exists(dcevm64Path)) {
            Files.deleteIfExists(dcevm64Path.resolve(config.getLibraryName()));
            Files.deleteIfExists(dcevm64Path.resolve(config.getBackupLibraryName()));
            Files.delete(dcevm64Path);
        }

    }

    public List<Installation> listInstallations() {
        return scanPaths(config.paths());
    }

    public ConfigurationInfo getConfiguration() {
        return config;
    }

    private void installClientServer(String javaVersion, Path path, boolean bit64) throws IOException, DcevmPatchNotFoundException {
        String resource = getVersionDir(javaVersion) + "/" + config.getResourcePath(bit64) + "/product/" + config.getLibraryName();

        Path library = path.resolve(config.getLibraryName());
        Path backup = path.resolve(config.getBackupLibraryName());

        try {
            // install actual DCEVM file
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    String version = javaVersion + (bit64 ? " (64 bit)" : "");
                    throw new DcevmPatchNotFoundException(version, path.toString());
                }

                // backup any existing library (assume original JVM file)
                if (Files.exists(library)) {
                    Files.move(library, backup);
                }

                // install the new file
                Files.copy(in, library);
            }
        } catch (NullPointerException | IOException e) {
            // try to restore original file
            if (Files.exists(backup)) {
                Files.move(backup, library, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        }
    }

    /**
     * Convert java version to a directory name containing associated installation resources.
     *
     * @param javaVersion full java version (such as 1.7.0_45)
     * @return directory in which the installer is available (such as 1.7)
     */
    private String getVersionDir(String javaVersion) {
        String[] version = javaVersion.split("[\\.\\_]");
        return version[0] + "." + version[1];
    }

    private void uninstallClientServer(Path path) throws IOException {
        Path library = path.resolve(config.getLibraryName());
        Path backup = path.resolve(config.getBackupLibraryName());

        if (Files.exists(backup)) {
            Files.delete(library);
            Files.move(backup, library);
        }
    }

    private List<Installation> scanPaths(String... dirPaths) {
        List<Installation> installations = new ArrayList<>();
        for (String dirPath : dirPaths) {
            Path dir = Paths.get(dirPath);
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    scanDirectory(stream, installations);
                } catch (Exception ex) {
                    // Ignore, try different directory
                    ex.printStackTrace();
                }
            }
        }
        return installations;
    }

    private void scanDirectory(DirectoryStream<Path> stream, List<Installation> installations) {
        for (Path path : stream) {
            if (Files.isDirectory(path) && (config.isJDK(path) || config.isJRE(path))) {
                try {
                    Installation inst = new Installation(config, path);
                    if (!installations.contains(inst)) {
                        installations.add(inst);
                    }
                } catch (Exception ex) {
                    // FIXME: just ignore the installation for now..
                    ex.printStackTrace();
                }
            }
        }
    }

}
