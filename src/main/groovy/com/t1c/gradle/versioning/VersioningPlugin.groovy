package com.t1c.gradle.versioning

import com.t1c.gradle.versioning.tasks.VersionDisplayTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class VersioningPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {
		project.extensions.create('versioning', VersioningExtension, project)
		project.tasks.create('versionDisplay', VersionDisplayTask)
	}

}