/*
 * Copyright 2015 Rod MacKenzie
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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.util.ConfigureUtil

class ServerPluginConfiguration {

    public static final String DOWNLOADS_DIR_PROPERTY = 'com.github.rodm.teamcity.downloadsDir'
    public static final String BASE_DOWNLOAD_URL_PROPERTY = 'com.github.rodm.teamcity.baseDownloadUrl'
    public static final String BASE_DATA_DIR_PROPERTY = 'com.github.rodm.teamcity.baseDataDir'
    public static final String BASE_HOME_DIR_PROPERTY = 'com.github.rodm.teamcity.baseHomeDir'

    public static final String DEFAULT_DOWNLOADS_DIR = 'downloads'
    public static final String DEFAULT_BASE_DOWNLOAD_URL = 'http://download.jetbrains.com/teamcity'
    public static final String DEFAULT_BASE_DATA_DIR = 'data'
    public static final String DEFAULT_BASE_HOME_DIR = 'servers'

    def descriptor

    private CopySpec files

    private Map<String, Object> tokens = [:]

    private String downloadsDir

    private String baseDownloadUrl

    private String baseDataDir

    private String baseHomeDir

    final NamedDomainObjectContainer<TeamCityEnvironment> environments

    private Project project

    ServerPluginConfiguration(Project project, NamedDomainObjectContainer<TeamCityEnvironment> environments) {
        this.project = project
        this.environments = environments
        this.files = project.copySpec {}
    }

    def descriptor(Closure closure) {
        descriptor = new ServerPluginDescriptor()
        ConfigureUtil.configure(closure, descriptor)
    }

    def files(Closure closure) {
        ConfigureUtil.configure(closure, files.addChild())
    }

    CopySpec getFiles() {
        return files
    }

    Map<String, Object> getTokens() {
        return tokens
    }

    def setTokens(Map<String, Object> tokens) {
        this.tokens = tokens
    }

    def tokens(Map<String, Object> tokens) {
        this.tokens += tokens
    }

    String getDownloadsDir() {
        downloadsDir ? downloadsDir : property(DOWNLOADS_DIR_PROPERTY, DEFAULT_DOWNLOADS_DIR)
    }

    String getBaseDownloadUrl() {
        baseDownloadUrl ? baseDownloadUrl : property(BASE_DOWNLOAD_URL_PROPERTY, DEFAULT_BASE_DOWNLOAD_URL)
    }

    String getBaseDataDir() {
        baseDataDir ? baseDataDir : property(BASE_DATA_DIR_PROPERTY, DEFAULT_BASE_DATA_DIR)
    }

    String getBaseHomeDir() {
        baseHomeDir ? baseHomeDir : property(BASE_HOME_DIR_PROPERTY, DEFAULT_BASE_HOME_DIR)
    }

    void environments(Closure config) {
        environments.configure(config)
    }

    private String property(String name, String defaultValue) {
        if (project.hasProperty(name)) {
            return project.property(name)
        }
        return defaultValue
    }
}
