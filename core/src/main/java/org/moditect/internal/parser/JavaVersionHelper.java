/*
 *  Copyright 2017 - 2018 The ModiTect authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.moditect.internal.parser;

import org.moditect.spi.log.Log;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper to extract and parse the Java version.
 * Alternatively from Java 9 it is possible to use the API java.lang.Runtime#version().
 *
 * @author Fabio Massimo Ercoli
 */
public final class JavaVersionHelper {

    public static Optional<Integer> resolveWithVersion(List<String> jdepsExtraArgs, Log log) {
        JavaVersionHelper versionHelper = new JavaVersionHelper(log);
        if (!versionHelper.resolveWithVersionIfMultiRelease()) {
            log.debug("Java version does not need to check if " + MULTI_RELEASE_ARGUMENT + " is set");
            return Optional.empty();
        }

        Optional<Integer> result = versionHelper.extractVersion(jdepsExtraArgs);
        if ( result.isPresent() ) {
            log.debug("Resolve with version: multi release is set to " + result.get());
        } else {
            log.debug("Resolve without version: multi release not set");
        }
        return result;
    }

    private static final String VERSION_REGEXP = "^(\\d+)\\.(\\d+)\\.(\\d+).*";
    private static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEXP);
    private static final String JAVA_VERSION_PROPERTY_NAME = "java.version";
    private static final String MULTI_RELEASE_ARGUMENT = "--multi-release";

    private final Log log;

    JavaVersionHelper() {
        this.log = null;
    }

    private JavaVersionHelper(Log log) {
        this.log = log;
    }

    Version javaVersion() {
        String versionString = System.getProperty(JAVA_VERSION_PROPERTY_NAME);
        debug(JAVA_VERSION_PROPERTY_NAME + " -> " + versionString);

        return javaVersion(versionString);
    }

    Version javaVersion(String versionString) {
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            warn("The java version " + versionString + " cannot be parsed as " + VERSION_REGEXP);
            return null;
        }

        try {
            Version version = new Version(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));

            debug("parsed.version -> " + version);
            return version;
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            error("The java version " + versionString + " has an invalid format. " + ex.getMessage());
            return null;
        }
    }

    private boolean resolveWithVersionIfMultiRelease() {
        Version version = javaVersion();
        if (version == null) {
            return false;
        }

        if (version.major >= 14) {
            debug("Detected JDK 14+");
            return true;
        }

        // See https://github.com/moditect/moditect/issues/141
        if (version.major == 11 && version.minor == 0 && version.mini >= 11) {
            debug("Detected JDK 11.0.11+");
            return true;
        }
        return false;
    }

    private Optional<Integer> extractVersion(List<String> jdepsExtraArgs) {
        for (int i = 0; i < jdepsExtraArgs.size(); i++) {
            String extraArg = jdepsExtraArgs.get(i);

            if (extraArg.startsWith(MULTI_RELEASE_ARGUMENT)) {
                if (extraArg.length() == MULTI_RELEASE_ARGUMENT.length()) {
                    // we expect the version number in the next argument
                    return extractVersionFromNextArgument(jdepsExtraArgs, i);
                }
                return extractVersionFromSameArgument(extraArg);
            }
        }

        debug("No version can be extracted from arguments: " + jdepsExtraArgs);
        return Optional.empty();
    }

    private Optional<Integer> extractVersionFromNextArgument(List<String> jdepsExtraArgs, int i) {
        if (i == jdepsExtraArgs.size() - 1) {
            // there is no next argument
            error("No argument value for " + MULTI_RELEASE_ARGUMENT);
            return Optional.empty();
        }

        String versionString = jdepsExtraArgs.get(i + 1);
        debug("Version extracted from the next argument: " + versionString);
        return parseVersionNumber(versionString);
    }

    private Optional<Integer> extractVersionFromSameArgument(String multiReleaseArgument) {
        if (multiReleaseArgument.length() < MULTI_RELEASE_ARGUMENT.length() + 2) {
            error("Invalid argument value for " + MULTI_RELEASE_ARGUMENT + ": " + multiReleaseArgument);
            return Optional.empty();
        }

        String versionString = multiReleaseArgument.substring(MULTI_RELEASE_ARGUMENT.length()+1);
        debug("Version extracted from the same argument: " + versionString);
        return parseVersionNumber(versionString);
    }

    private Optional<Integer> parseVersionNumber(String versionString) {
        try {
            return Optional.of(Integer.parseInt(versionString));
        } catch (NumberFormatException ex) {
            error("Invalid argument value for " + MULTI_RELEASE_ARGUMENT + ": " + versionString);
            return Optional.empty();
        }
    }

    private void debug(String message) {
        if (log != null) {
            log.debug(message);
        }
    }

    private void warn(String message) {
        if (log != null) {
            log.warn(message);
        }
    }

    private void error(String message) {
        if (log != null) {
            log.error(message);
        }
    }

    static class Version {
        private int major;
        private int minor;
        private int mini;

        private Version(int major, int minor, int mini) {
            this.major = major;
            this.minor = minor;
            this.mini = mini;
        }

        int major() {
            return major;
        }
        int minor() {
            return minor;
        }
        int mini() {
            return mini;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + mini;
        }
    }
}
