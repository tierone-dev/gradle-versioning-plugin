package com.t1c.gradle.versioning

import com.t1c.gradle.versioning.git.GitInfoService
import org.gradle.api.GradleException
import org.gradle.api.Project

class VersioningExtension {

    private static final Map<String, Closure<String>> DISPLAY_MODES = [
            full    : { branchType, branchId, base, build, full, extension ->
                //"${branchId}-${build}"
                "${branchId}"
            },
            snapshot: { branchType, branchId, base, build, full, extension ->
                "${base}${extension.snapshot}"
            },
            base    : { branchType, branchId, base, build, full, extension ->
                base
            },
    ]

    private static final Map<String, Closure<String>> RELEASE_MODES = [
            tag : { nextTag, lastTag, currentTag, extension ->
                lastTag
            },
            snapshot: { nextTag, lastTag, currentTag, extension ->
                currentTag ?: "${nextTag}${extension.snapshot}"
            },
    ]

    String gitRepoRootDir = null

    /**
     * Getting the version type from a branch. Default: getting the part before the first "/" (or a second
     * optional 'separator' parameter). If no slash is found, takes the branch name as whole.
     *
     * For example:
     *
     * * release/2.0 --> release
     * * feature/2.0 --> feature
     * * master --> master
     */
    Closure<ReleaseInfo> releaseParser = { SCMInfo scmInfo, String separator = '/' ->
        List<String> part = scmInfo.branch.split(separator, 2) + ''
        new ReleaseInfo(type: part[0], base: part[1])
    }

    Closure<String> full = { SCMInfo scmInfo -> "${normalise(scmInfo.branch)}-${scmInfo.abbreviated}" }

    // Set of eligible branch types for computing a display version from the branch base name
    Set<String> releases = ['release'] as Set<String>

    def displayMode = 'full'
    def releaseMode = 'tag'
    String snapshot = '-SNAPSHOT'

    Closure<String> dirty = { version -> "${version}${dirtySuffix}" }
    String dirtySuffix = '-dirty'

	// If set to <code>true</code>, the build will fail if working copy is dirty and if the branch type is
	// part of the {@link #releases} list ("release" only by default).
    boolean dirtyFailOnReleases = false

    boolean noWarningOnDirty = false;

    /**
     * Computed version information
     */
    private VersionInfo info

    /**
     * Linked project
     */
    private final Project project

    /**
     * Constructor
     * @param project Linked project
     */
    VersioningExtension(Project project) {
        this.project = project
    }

    VersionInfo getInfo() {
        if (!info) {
            info = computeInfo()
        }
        info
    }

    /**
     * Computes the version information.
     */
    VersionInfo computeInfo() {

        SCMInfoService scmInfoService = new GitInfoService()
        SCMInfo scmInfo = scmInfoService.getInfo(project, this)

        if (scmInfo == SCMInfo.NONE) {
            return VersionInfo.NONE
        }

        // Branch parsing
        ReleaseInfo releaseInfo = releaseParser(scmInfo, scmInfoService.branchTypeSeparator)
        String versionReleaseType = releaseInfo.type
        String versionBase = releaseInfo.base

        // Branch identifier
        String versionBranchId = normalise(scmInfo.branch)

        // Full version
        String versionFull = full(scmInfo)

        // Display version
        String versionDisplay
        if (versionReleaseType in releases) {
            List<String> baseTags = scmInfoService.getBaseTags(project, this, versionBase)
            versionDisplay = getDisplayVersion(scmInfo, releaseInfo, baseTags)
        } else {
            // Adjusting the base
            def base = versionBase ?: versionBranchId
            // Display mode
            if (displayMode instanceof String) {
                def mode = DISPLAY_MODES[displayMode as String]
                if (mode) {
                    versionDisplay = mode(versionReleaseType, versionBranchId, base, scmInfo.abbreviated, versionFull, this)
                } else {
                    throw new GradleException("${mode} is not a valid display mode.")
                }
            } else if (displayMode instanceof Closure) {
                def mode = displayMode as Closure
                versionDisplay = mode(versionReleaseType, versionBranchId, base, scmInfo.abbreviated, versionFull, this)
            } else {
                throw new GradleException("The `displayMode` must be a registered default mode or a Closure.")
            }
        }

        // Dirty update
        if (scmInfo.dirty) {
            if (dirtyFailOnReleases && versionReleaseType in releases) {
                throw new GradleException("Dirty working copy - cannot compute version.")
            } else {
                if (!noWarningOnDirty) {
                    println "[versioning] WARNING - the working copy has unstaged or uncommitted changes."
                }
                versionDisplay = dirty(versionDisplay)
                versionFull = dirty(versionFull)
            }
        }

        new VersionInfo(
                scm: 'git',
                branch: scmInfo.branch,
                branchType: versionReleaseType,
                branchId: versionBranchId,
                full: versionFull,
                base: versionBase,
                display: versionDisplay,
                commit: scmInfo.commit,
                build: scmInfo.abbreviated,
                tag: scmInfo.tag,
                dirty: scmInfo.dirty,
                shallow: scmInfo.shallow,
        )
    }

    private String getDisplayVersion(SCMInfo scmInfo, ReleaseInfo releaseInfo, List<String> baseTags) {
        String currentTag = scmInfo.tag
        if (scmInfo.shallow) {
            // In case the repository has no history (shallow clone or check out), the last
            // tags cannot be get and the display version cannot be computed correctly.
            if (currentTag) {
                // The only special case is when the HEAD commit is exactly on a tag and we can use it
                return currentTag
            } else {
                // In any other case, we can only start from the base information and add a snapshot information
                return "${releaseInfo.base}${snapshot}"
            }
        } else {
            String lastTag
            String nextTag
            if (baseTags.empty) {
                lastTag = ''
                nextTag = "${releaseInfo.base}.0"
            } else {
                lastTag = baseTags[0].trim().replace('v','')
                def lastNumber = (lastTag =~ /${releaseInfo.base}\.(\d+)/)[0][1] as int
                def newNumber = lastNumber + 1
                nextTag = "${releaseInfo.base}.${newNumber}"
            }
            Closure<String> mode
            if (releaseMode instanceof String) {
                mode = RELEASE_MODES[releaseMode]
                if (!mode) {
                    throw new GradleException("${releaseMode} is not a valid release mode.")
                }
            } else if (releaseMode instanceof Closure) {
                mode = releaseMode as Closure
            } else {
                throw new GradleException("The `releaseMode` must be a registered default mode or a Closure.")
            }
            return mode(nextTag, lastTag, currentTag, this)
        }
    }

    public static String normalise(String value) {
        value.replaceAll(/[^A-Za-z0-9\.\-_]/, '-')
    }

}