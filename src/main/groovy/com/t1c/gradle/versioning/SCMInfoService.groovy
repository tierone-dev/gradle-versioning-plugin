package com.t1c.gradle.versioning

import org.gradle.api.Project

interface SCMInfoService {

    SCMInfo getInfo(Project project, VersioningExtension extension)

    List<String> getBaseTags(Project project, VersioningExtension extension, String base)

    String getBranchTypeSeparator()

}