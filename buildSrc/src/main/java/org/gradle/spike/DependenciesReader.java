/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.spike;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.internal.impldep.com.google.common.collect.Iterables;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class DependenciesReader {

    private static final String DEFAULT_DEPENDENCIES_FILE = "dependencies.yaml";

    private final Project project;

    public DependenciesReader(Project project) {
        this.project = project;
    }

    private void readDependencies(File source) {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(source.toPath())) {
            Map<String, Object> dependencies = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (Map.Entry<String, Object> entry : dependencies.entrySet()) {
                declareDependenciesForConfiguration(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void declareDependenciesForConfiguration(String configuration, Object dependencies) {
        project.getConfigurations().all(c -> {
            if (configuration.equals(c.getName())) {
                List<Object> asList = (List<Object>) dependencies;
                DependencyHandler handler = project.getDependencies();
                for (Object o : asList) {
                    Map<String, Object> props = (Map<String, Object>) o;
                    Object module = props.get("module");
                    Object platform = props.get("platform");
                    Object testFixtures = props.get("test-fixtures");
                    Object project = props.get("project");
                    Dependency dep = null;
                    if (module != null) {
                        dep = handler.add(configuration, module);
                    } else if (platform != null) {
                        dep = handler.add(configuration, handler.platform(platform));
                    } else if (testFixtures != null) {
                        dep = handler.add(configuration, handler.testFixtures(testFixtures));
                    } else if (project != null) {
                        dep = handler.add(configuration, handler.project((Map) project));
                    }
                    if (dep instanceof ExternalModuleDependency) {
                        Object require = props.get("require");
                        Object strictly = props.get("strictly");
                        Object prefer = props.get("prefer");
                        Object reject = props.get("reject");
                        Object branch = props.get("branch");
                        Object because = props.get("because");
                        ((ExternalModuleDependency) dep).version(v -> {
                            if (require != null) {
                                v.require(asString(require));
                            }
                            if (strictly != null) {
                                v.strictly(asString(strictly));
                            }
                            if (prefer != null) {
                                v.prefer(asString(prefer));
                            }
                            if (reject != null) {
                                if (reject instanceof String) {
                                    v.reject(asString(reject));
                                } else if (reject instanceof Iterable) {
                                    v.reject(Iterables.toArray((Iterable<String>) reject, String.class));
                                }
                            }
                            if (branch != null) {
                                v.setBranch(asString(branch));
                            }
                        });
                        if (because != null) {
                            dep.because(asString(because));
                        }
                    }
                }
            }
        });
    }

    private String asString(Object require) {
        return String.valueOf(require).intern();
    }

    public static void readDependencies(Project project) {
        File source = project.file(DEFAULT_DEPENDENCIES_FILE);
        DependenciesReader reader = new DependenciesReader(project);
        reader.readDependencies(source);
    }
}
