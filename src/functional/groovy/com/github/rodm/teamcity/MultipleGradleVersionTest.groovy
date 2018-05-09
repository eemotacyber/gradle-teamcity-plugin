/*
 * Copyright 2016 Rod MacKenzie
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
package com.github.rodm.teamcity

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.zip.ZipFile

import static org.gradle.api.JavaVersion.VERSION_1_7
import static org.gradle.api.JavaVersion.VERSION_1_8
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.anything
import static org.hamcrest.Matchers.isOneOf
import static org.junit.Assume.assumeThat

@RunWith(Parameterized)
class MultipleGradleVersionTest {

    static final String NO_DEFINITION_WARNING = TeamCityPlugin.NO_DEFINITION_WARNING_MESSAGE.substring(4)

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder()

    private File buildFile

    @Parameterized.Parameter(0)
    public String version

    @Parameterized.Parameters(name = 'Gradle {0}')
    static List<String> data() {
        return [
                '4.0.2', '4.1', '4.2.1', '4.3.1', '4.4.1', '4.5.1', '4.6', '4.7'
        ]
    }

    @Before
    void setup() throws IOException {
        buildFile = projectDir.newFile("build.gradle")

        buildFile << """
            plugins {
                id 'java'
                id 'com.github.rodm.teamcity-server'
            }

            project(':common') {
                apply plugin: 'java'
                apply plugin: 'com.github.rodm.teamcity-common'

                teamcity {
                    version = '8.1.5'
                }
            }

            project(':agent') {
                apply plugin: 'java'
                apply plugin: 'com.github.rodm.teamcity-agent'

                dependencies {
                    compile project(':common')
                }

                teamcity {
                    version = '8.1.5'

                    agent {
                        descriptor {
                            pluginDeployment {
                                useSeparateClassloader = false
                            }
                        }
                    }
                }
            }

            dependencies {
                compile project(':common')
                agent project(path: ':agent', configuration: 'plugin')
            }

            teamcity {
                version = '8.1.5'

                server {
                    descriptor {
                        name = 'test-plugin'
                        displayName = 'Test plugin'
                        version = '1.0'
                        vendorName = 'vendor name'
                    }
                }
            }
        """

        File settingsFile = projectDir.newFile('settings.gradle')
        settingsFile << """
            rootProject.name = 'test-plugin'

            include 'common'
            include 'agent'
        """

        File commonJavaDir = projectDir.newFolder('common', 'src', 'main', 'java', 'example', 'common')
        File commonJavaFile = new File(commonJavaDir, 'ExampleBuildFeature.java')
        commonJavaFile << """
            package example.common;

            import jetbrains.buildServer.util.StringUtil;

            public class ExampleBuildFeature {
                public static final String BUILD_FEATURE_NAME = "example";
            }
        """

        File agentJavaDir = projectDir.newFolder('agent', 'src', 'main', 'java', 'example', 'agent')
        File agentJavaFile = new File(agentJavaDir, 'ExampleBuildFeature.java')
        agentJavaFile << """
            package example.agent;

            import jetbrains.buildServer.agent.AgentLifeCycleAdapter;

            public class ExampleBuildFeature extends AgentLifeCycleAdapter {
                String FEATURE_NAME = example.common.ExampleBuildFeature.BUILD_FEATURE_NAME;
            }
        """

        File agentMetaInfDir = projectDir.newFolder('agent', 'src', 'main', 'resources', 'META-INF')
        File agentDefinitionFile = new File(agentMetaInfDir, 'build-agent-plugin-example.xml')
        agentDefinitionFile << """<?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://www.springframework.org/schema/beans"
                   xsi:schemaLocation="http://www.springframework.org/schema/beans
                                       http://www.springframework.org/schema/beans/spring-beans-3.0.xsd"
                   default-autowire="constructor">
                <bean id="exampleFeature" class="example.agent.ExampleBuildFeature"></bean>
            </beans>
        """

        File serverJavaDir = projectDir.newFolder('src', 'main', 'java', 'example', 'server')
        File serverJavaFile = new File(serverJavaDir, 'ExampleServerPlugin.java')
        serverJavaFile << """
            package example.server;

            import example.common.ExampleBuildFeature;
            import jetbrains.buildServer.serverSide.BuildServerAdapter;

            public class ExampleServerPlugin extends BuildServerAdapter {
                String FEATURE_NAME = ExampleBuildFeature.BUILD_FEATURE_NAME;
            }
        """

        File serverMetaInfDir = projectDir.newFolder('src', 'main', 'resources', 'META-INF')
        File serverDefinitionFile = new File(serverMetaInfDir, 'build-server-plugin-example.xml')
        serverDefinitionFile << """<?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns="http://www.springframework.org/schema/beans"
                   xsi:schemaLocation="http://www.springframework.org/schema/beans
                                       http://www.springframework.org/schema/beans/spring-beans-3.0.xsd"
                   default-autowire="constructor">
                <bean id="examplePlugin" class="example.server.ExampleServerPlugin"></bean>
            </beans>
        """
    }

    @Test
    void 'build plugin'() {
        assumeThat(JavaVersion.current(), supportsGradle(version))

        BuildResult result = executeBuild(version)
        checkBuild(result)
    }

    Matcher supportsGradle(String version) {
        return version < '4.3' ? isOneOf(VERSION_1_7, VERSION_1_8) : anything()
    }

    private BuildResult executeBuild(String version) {
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments('build')
                .withPluginClasspath()
                .withGradleVersion(version)
                .build()
        return result
    }

    private void checkBuild(BuildResult result) {
        assertThat(result.task(":agent:agentPlugin").getOutcome(), is(SUCCESS))
        assertThat(result.task(":serverPlugin").getOutcome(), is(SUCCESS))

        assertThat(result.getOutput(), not(containsString(NO_DEFINITION_WARNING)))
        assertThat(result.getOutput(), not(containsString('but the implementation class')))

        ZipFile agentPluginFile = new ZipFile(new File(projectDir.root, 'agent/build/distributions/test-plugin-agent.zip'))
        List<String> agentEntries = agentPluginFile.entries().collect { it.name }
        assertThat(agentEntries, hasItem('teamcity-plugin.xml'))
        assertThat(agentEntries, hasItem('lib/common.jar'))
        assertThat(agentEntries, hasItem('lib/agent.jar'))

        ZipFile serverPluginFile = new ZipFile(new File(projectDir.root, 'build/distributions/test-plugin.zip'))
        List<String> serverEntries = serverPluginFile.entries().collect { it.name }
        assertThat(serverEntries, hasItem('agent/test-plugin-agent.zip'))
        assertThat(serverEntries, hasItem('server/common.jar'))
        assertThat(serverEntries, hasItem('server/test-plugin.jar'))
        assertThat(serverEntries, hasItem('teamcity-plugin.xml'))
    }
}
