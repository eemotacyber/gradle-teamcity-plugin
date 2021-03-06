/*
 * Copyright 2018 Rod MacKenzie
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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class ValidateExecutableFileActionTest {

    private static final String AGENT_PLUGIN_DESCRIPTOR = '''<?xml version="1.0" encoding="UTF-8"?>
        <teamcity-agent-plugin>
          <tool-deployment>
            <layout>
              <executable-files>
                <include name="test1"/>
                <include name="bin/test2"/>
              </executable-files>
            </layout>
          </tool-deployment>
        </teamcity-agent-plugin>
    '''

    private static final String MISSING_EXECUTABLE_FILE_WARNING = TeamCityAgentPlugin.MISSING_EXECUTABLE_FILE_WARNING.substring(4)

    private final ResettableOutputEventListener outputEventListener = new ResettableOutputEventListener()

    @Rule
    public final TemporaryFolder projectDir = new TemporaryFolder()

    @Rule
    public final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    private Project project
    private Task stubTask

    @Before
    void setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir.root).build()
        stubTask = mock(Task)
    }

    private static FileCopyDetails fileCopyDetails(String path) {
        FileCopyDetails fileCopyDetails = mock(FileCopyDetails)
        when(fileCopyDetails.getPath()).thenReturn(path)
        return fileCopyDetails
    }

    @Test
    void 'output warning when executable file is missing'() {
        File descriptorFile = project.file('teamcity-plugin.xml')
        descriptorFile << AGENT_PLUGIN_DESCRIPTOR
        Set<FileCopyDetails> files = [fileCopyDetails('test1')]
        Action<Task> validationAction = new TeamCityAgentPlugin.PluginExecutableFilesValidationAction(descriptorFile, files)
        outputEventListener.reset()

        validationAction.execute(stubTask)

        String message = String.format(MISSING_EXECUTABLE_FILE_WARNING, 'bin/test2')
        assertThat(outputEventListener.toString(), containsString(message))
    }

    @Test
    void 'does not output warning when executable file is present'() {
        File descriptorFile = project.file('teamcity-plugin.xml')
        descriptorFile << AGENT_PLUGIN_DESCRIPTOR
        Set<FileCopyDetails> files = [fileCopyDetails('test1'), fileCopyDetails('bin/test2')]
        Action<Task> validationAction = new TeamCityAgentPlugin.PluginExecutableFilesValidationAction(descriptorFile, files)
        outputEventListener.reset()

        validationAction.execute(stubTask)

        String message = String.format(MISSING_EXECUTABLE_FILE_WARNING, 'bin/test2')
        assertThat(outputEventListener.toString(), not(containsString(message)))
    }
}
