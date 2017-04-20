package com.t1c.gradle.versioning

import groovy.transform.Canonical

@Canonical
class VersionInfo {

    static final VersionInfo NONE = new VersionInfo()

    String scm = 'n/a'
    String branch = ''
    String branchType = ''
    String branchId = ''
    String commit = ''
    String display = ''
    String full = ''
    String base = ''
    String build = ''
    String tag = null
    boolean dirty = false
    boolean shallow = false

}