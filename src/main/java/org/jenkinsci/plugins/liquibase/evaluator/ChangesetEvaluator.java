package org.jenkinsci.plugins.liquibase.evaluator;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import liquibase.exception.MigrationFailedException;

import java.util.Properties;

import org.jenkinsci.plugins.liquibase.common.LiquibaseCommand;
import org.jenkinsci.plugins.liquibase.common.LiquibaseProperty;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jenkins builder which evaluates liquibase changesets.
 */
@SuppressWarnings("ProhibitedExceptionThrown")
public class ChangesetEvaluator extends AbstractLiquibaseBuilder {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final Logger LOG = LoggerFactory.getLogger(ChangesetEvaluator.class);

    protected boolean testRollbacks;
    private boolean dropAll;
    protected boolean tagOnSuccessfulBuild;

    public ChangesetEvaluator() {
        super();
    }

    @Override
    public void runPerform(Run<?, ?> build,
                           TaskListener listener,
                           Liquibase liquibase,
                           Contexts contexts,
                           ExecutedChangesetAction executedChangesetAction,
                           Properties configProperties) {

        executedChangesetAction.setRollbacksTested(testRollbacks);

        try {
            String resolvedCommand;
            if (isTestRollbacks()) {
                resolvedCommand = LiquibaseCommand.UPDATE_TESTING_ROLLBACKS.getCommand();
            } else {
                resolvedCommand = LiquibaseCommand.UPDATE.getCommand();
            }

            if (dropAll) {
                listener.getLogger().println("Running liquibase dropAll");
                liquibase.dropAll();
            }
            listener.getLogger().println("Running liquibase command '" + resolvedCommand + "'");

            LabelExpression labelExpression =
                    new LabelExpression(getProperty(configProperties, LiquibaseProperty.LABELS));
            if (LiquibaseCommand.UPDATE_TESTING_ROLLBACKS.isCommand(resolvedCommand)) {
                liquibase.updateTestingRollback(contexts, labelExpression);
            }
            if (LiquibaseCommand.UPDATE.isCommand(resolvedCommand)) {
                liquibase.update(contexts, labelExpression);
            }

            if (tagOnSuccessfulBuild) {
                String tagString = build.getParent().getName() + "-" + build.getNumber();
                listener.getLogger().println("Applying tag '" + tagString + "' to schema");
                liquibase.tag(tagString);
                executedChangesetAction.setAppliedTag(tagString);
            }
        } catch (MigrationFailedException migrationException) {
            migrationException.printStackTrace(listener.getLogger());
            build.setResult(Result.UNSTABLE);
        } catch (LiquibaseException e) {
            e.printStackTrace(listener.getLogger());
            build.setResult(Result.FAILURE);
        }

    }

    @DataBoundConstructor
    public ChangesetEvaluator(String databaseEngine,
                              String changeLogFile,
                              String url,
                              String defaultSchemaName,
                              String contexts,
                              String liquibasePropertiesPath,
                              String classpath,
                              String driverClassname,
                              String changeLogParameters,
                              boolean testRollbacks,
                              boolean dropAll,
                              String labels,
                              String basePath,
                              boolean tagOnSuccessfulBuild,
                              boolean useIncludedDriver,
                              String credentialsId) {
        super(databaseEngine, changeLogFile, url, defaultSchemaName, contexts,
                liquibasePropertiesPath,
                classpath, driverClassname, changeLogParameters, labels, basePath, useIncludedDriver, credentialsId);
        this.testRollbacks = testRollbacks;
        this.dropAll = dropAll;
        this.tagOnSuccessfulBuild = tagOnSuccessfulBuild;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public boolean isTestRollbacks() {
        return testRollbacks;
    }

    @DataBoundSetter
    public void setTestRollbacks(boolean testRollbacks) {
        this.testRollbacks = testRollbacks;
    }

    public boolean isDropAll() {
        return dropAll;
    }

    @DataBoundSetter
    public void setDropAll(boolean dropAll) {
        this.dropAll = dropAll;
    }


    public boolean isTagOnSuccessfulBuild() {
        return tagOnSuccessfulBuild;
    }

    @DataBoundSetter
    public void setTagOnSuccessfulBuild(boolean tagOnSuccessfulBuild) {
        this.tagOnSuccessfulBuild = tagOnSuccessfulBuild;
    }

    public static class DescriptorImpl extends AbstractLiquibaseDescriptor {

        public DescriptorImpl() {
            load();
        }

        public DescriptorImpl(Class<? extends ChangesetEvaluator> clazz) {
            super(clazz);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Evaluate liquibase changesets";
        }
    }

}