/**
 * Copyright 2014 Transmode AB
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
package se.transmode.gradle.plugins.docker
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class DockerTaskTest {

    private static final String TASK_NAME = 'dockerTask'
    private static final ArrayList<String> TEST_ENV = ['foo', 'bar']
    private static final String TEST_TARGET_DIR = 'testTargetDir'
    private static final String TEST_MAINTAINER = 'john doe'
    private static final ArrayList<String> TEST_INSTRUCTIONS = [
            'FROM ubuntu:14.04',
            'ADD foo/bar /',
            'RUN echo hello world'
    ]

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder()

    private Project createProject() {
        def project = ProjectBuilder.builder().build()
        project.extensions.create(DockerPlugin.EXTENSION_NAME, DockerPluginExtension)
        return project
    }

    private Task createTask(Project project) {
        return project.task(TASK_NAME, type: DockerTask)
    }

    @Test
    public void addTaskToProject() {
        def task = createTask(createProject())
        assertThat task, is(instanceOf(DockerTask))
        assertThat task.name, is(equalTo(TASK_NAME))
    }

    @Test
    public void defineExposePort() {
        def task = createTask(createProject())
        task.exposePort(99)
        assertThat "EXPOSE ${99}".toString(), isIn(task.buildDockerfile().instructions)
    }

    @Test
    public void defineExposePorts() {
        def task = createTask(createProject())
        task.exposePort(99, 100, 101)
        assertThat "EXPOSE ${99} ${100} ${101}".toString(), isIn(task.buildDockerfile().instructions)
    }

    @Test
    public void nonJavaDefaultBaseImage() {
        def project = createProject()
        def task = createTask(project)
        assertThat task.baseImage, is(equalTo(DockerTask.DEFAULT_IMAGE))
    }

    @Test
    public void overrideBaseImageInExtension() {
        def project = createProject()
        def task = createTask(project)
        project[DockerPlugin.EXTENSION_NAME].baseImage = "extensionBase"
        assertThat task.baseImage, is(equalTo("extensionBase"))
    }

    @Test
    public void overrideBaseImageInTask() {
        def task = createTask(createProject())
        task.baseImage = "taskBase"
        assertThat task.baseImage, is(equalTo("taskBase"))
    }

    @Test
    public void determineBaseImageFromTargetCompatibilityIfNotOverriden() {
        def project = createProject()
        project.apply plugin: 'java'
        def task = createTask(project)
        def testVersion = JavaVersion.VERSION_1_6
        project.targetCompatibility = testVersion
        assertThat project[DockerPlugin.EXTENSION_NAME].baseImage, is(nullValue())
        assertThat task.baseImage,
                is(equalTo(JavaBaseImage.imageFor(testVersion).imageName))
    }

    @Test
    public void testAddFileWithDir() {
        def project = createProject()
        def task = createTask(project)
        
        // Get directory to use
        URL dir_url = ClassLoader.getSystemResource(TEST_TARGET_DIR)
        File dir = new File(dir_url.toURI())
        assertThat(dir.isDirectory(), equalTo(true))
        
        // Add the directory and do the work to move it to the staging directory
        task.addFile(dir)
        task.setupStageDir()
        
        // Confirm that the directory was copied under the staging dir
        File targetDir = new File(task.stageDir, TEST_TARGET_DIR)
        assertThat(targetDir.exists(), equalTo(true))
        assertThat(targetDir.isDirectory(), equalTo(true))
        assertThat(targetDir.list().length, equalTo(dir.list().length))
    }

    @Test
    public void buildDockerfileFromFileAndExtend() {
        def project = createProject()
        def task = createTask(project)
        // write base dockerfile to file
        def dockerfile = testFolder.newFile('Dockerfile')
        dockerfile.withWriter { out ->
            TEST_INSTRUCTIONS.each { out.writeLine(it) }
        }
        task.dockerfile = dockerfile
        // add instructions to dockerfile
        task.maintainer = TEST_MAINTAINER
        task.setEnvironment(*TEST_ENV)
        assertThat(task.buildDockerfile().instructions,
                contains(*TEST_INSTRUCTIONS,
                        "MAINTAINER ${TEST_MAINTAINER}".toString(),
                        "ENV ${TEST_ENV.join(' ')}".toString()))
    }
}
