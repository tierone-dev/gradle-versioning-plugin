package com.t1c.gradle.versioning.git

import com.t1c.gradle.versioning.SCMInfo
import com.t1c.gradle.versioning.SCMInfoService
import com.t1c.gradle.versioning.VersioningExtension
import org.ajoberstar.grgit.Commit
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Status
import org.ajoberstar.grgit.Tag
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.GradleException
import org.gradle.api.Project

import static org.eclipse.jgit.lib.Constants.R_TAGS

class GitInfoService implements SCMInfoService {

    @Override
    SCMInfo getInfo(Project project, VersioningExtension extension) {
        
        boolean hasGit = project.rootProject.file('.git').exists() ||
                project.file('.git').exists() ||
                (extension.gitRepoRootDir != null &&
                        new File(extension.gitRepoRootDir, '.git').exists())
        
        if (!hasGit) {
            SCMInfo.NONE
        } else {
            File gitDir = extension.gitRepoRootDir ?
                new File(extension.gitRepoRootDir) :
                project.projectDir

            //noinspection GroovyAssignabilityCheck
            def grgit = Grgit.open(currentDir: gitDir)

            String branch = grgit.branch.current.name

            List<Commit> commits = grgit.log(maxCommits: 1)
            if (commits.empty) {
                throw new GradleException("No commit available in the repository - cannot compute version")
            }

            def lastCommit = commits[0]
            String commit = lastCommit.id
            String abbreviated = lastCommit.abbreviatedId
            boolean shallow = lastCommit.parentIds.empty

            String tag
            
            // Cannot use the `describe` command if the repository is shallow
            if (shallow) {
                Map<ObjectId, Ref> tags = new HashMap<ObjectId, Ref>();

                def gitRepository = grgit.repository.jgit.repository

                for (Ref r : gitRepository.refDatabase.getRefs(R_TAGS).values()) {
                    ObjectId key = gitRepository.peel(r).getPeeledObjectId();
                    if (key == null)
                        key = r.getObjectId();
                    tags.put(key, r);
                }
                // If we're on a tag, we can use it directly
                Ref lucky = tags.get(gitRepository.resolve(Constants.HEAD))
                if (lucky != null) {
                    tag = lucky.name.substring(R_TAGS.length());
                }
                // If not, we do not go further
                else {
                    tag = null
                }
            } else {
                String described = grgit.repository.jgit.describe().setLong(true).call()
                if (described) {
                    // The format returned by the long version of the `describe` command is: <tag>-<number>-<commit>
                    def m = described =~ /^(.*)-(\d+)-g([0-9a-f]+)$/
                    if (m.matches()) {
                        def count = m.group(2) as int
                        if (count == 0) {
                            tag = m.group(1)
                        } else {
                            tag = null
                        }
                    } else {
                        throw new GradleException("Cannot get parse description of current commit: ${described}")
                    }
                } else {// no previous tags
                    tag = null
                }
            }

            new SCMInfo(
                    branch: branch,
                    commit: commit,
                    abbreviated: abbreviated,
                    dirty: isGitTreeDirty(gitDir),
                    tag: tag,
                    shallow: shallow,
            )
        }
    }

    protected static File getGitDirectory(VersioningExtension extension, Project project) {
        return extension.gitRepoRootDir ?
                new File(extension.gitRepoRootDir) :
                project.projectDir
    }

    static boolean isGitTreeDirty(File dir) {// Open the Git repo
        //noinspection GroovyAssignabilityCheck
        Status status = Grgit.open(currentDir: dir).status()
        return !isClean(status)
    }

    private static boolean isClean(Status status) {
        return status.staged.allChanges.empty &&
                status.unstaged.allChanges.findAll { !it.startsWith('userHome/') }.empty
    }

    @Override
    List<String> getBaseTags(Project project, VersioningExtension extension, String base) {
    	def baseTagPattern = /^v${base}\.(\d+)$/
        //noinspection GroovyAssignabilityCheck
        def grgit = Grgit.open(currentDir: getGitDirectory(extension, project))
        return grgit.tag.list()
                .findAll { it.name ==~ baseTagPattern }
                .sort { -it.commit.time }
        // ... (#36) commit time is not enough. We have also to consider the case where several pattern compliant tags
		//are on the same commit, and we must sort them by desc version
                .sort { -((it.name - "v${base}.") as int) }
                .collect { it.name }
    }

    @Override
    String getBranchTypeSeparator() {
        '/'
    }
}
