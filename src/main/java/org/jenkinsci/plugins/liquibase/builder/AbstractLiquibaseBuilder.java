package org.jenkinsci.plugins.liquibase.builder;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import hudson.*;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.liquibase.common.LiquibaseProperty;
import org.jenkinsci.plugins.liquibase.common.PropertiesAssembler;
import org.jenkinsci.plugins.liquibase.install.LiquibaseInstallation;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Properties;

public abstract class AbstractLiquibaseBuilder extends Builder implements SimpleBuildStep {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLiquibaseBuilder.class);

    protected String installationName;

    protected String changeLogFile;
    protected String url;
    protected String contexts;
    protected String liquibasePropertiesPath;
    protected String labels;
    private String changeLogParameters;
    private String resourceDirectories;
    private String credentialsId;

    @Deprecated
    protected transient String username;
    @Deprecated
    protected transient String password;

    public AbstractLiquibaseBuilder() {

    }

    public LiquibaseInstallation getInstallation(EnvVars env, TaskListener listener, FilePath workspace) throws IOException, InterruptedException {
        LiquibaseInstallation foundInstallation = null;
        if (installationName != null) {
            final LiquibaseInstallation.DescriptorImpl installDescriptor = ToolInstallation.all().get(LiquibaseInstallation.DescriptorImpl.class);
            if (installDescriptor != null) {
                for (LiquibaseInstallation i : installDescriptor.getInstallations()) {
                    if (installationName.equals(i.getName())) {
                        foundInstallation = i;
                        break;
                    }
                }
            }
        }

        if (foundInstallation == null) {
            return null;
        }

        Computer computer = workspace.toComputer();
        if (computer == null) {
            return null;
        }
        Node node = computer.getNode();
        if (node == null) {
            return null;
        }

        LiquibaseInstallation returnInstaller = foundInstallation.forNode(node, listener);
        returnInstaller = returnInstaller.forEnvironment(env);

        return returnInstaller;
    }

    public String getInstallationName() {
        return installationName;
    }

    @DataBoundSetter
    public void setInstallationName(String installationName) {
        this.installationName = installationName;
    }

    protected Object readResolve() {
        return this;
    }

    abstract public Descriptor<Builder> getDescriptor();

    @Override
    public void perform(@Nonnull Run<?, ?> build,
                        @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {

        final PrintStream log = listener.getLogger();
        log.println("\n\nRunning "+getDescriptor().getDisplayName()+"....");

        final EnvVars environment = build.getEnvironment(listener);

        Properties configProperties = PropertiesAssembler.createLiquibaseProperties(this, build, environment, workspace);

        LiquibaseInstallation installation = getInstallation(environment, listener, workspace);
        if (installation == null) {
            listener.fatalError("Liquibase installation was not found.");
            build.setResult(Result.NOT_BUILT);
            return;
        }
        log.println("Liquibase home: "+installation.getHome());

        if (!installation.isValidLiquibaseHome()) {
            listener.fatalError("Liquibase installation "+installation.getHome()+" is not a valid Liquibase install");
            build.setResult(Result.NOT_BUILT);
            return;
        }


        if (!Strings.isNullOrEmpty(installation.getDatabaseDriverUrl())) {
            Iterable<String> urls = Splitter.on(",").trimResults().split(installation.getDatabaseDriverUrl());
            for (String url : urls) {
                String filename = url.substring(url.lastIndexOf("/") + 1);
                File localJar = new File(installation.getHome(), "lib/" + filename);
                if (!localJar.exists()) {
                    log.println("Downloading " + url + " to " + localJar);
                    URL downloadUrl = new URL(url);
                    new FilePath(localJar).copyFrom(downloadUrl);
                }
            }
        }


        String liquibaseCmd;
        if (Functions.isWindows()) {
            liquibaseCmd = installation.getHome()+"\\liquibase.bat";
        } else {
            liquibaseCmd = installation.getHome()+"/liquibase";
        }

        ArgumentListBuilder cliCommand = new ArgumentListBuilder(liquibaseCmd);
        addGlobalArguments(cliCommand, configProperties, build, environment, listener);
        addCommandAndArguments(cliCommand, configProperties, build, environment, listener);

        int exitStatus = launcher.launch().cmds(cliCommand).stdout(listener).join();
        boolean result = didErrorsOccur(build, exitStatus);
        if (!result) {
            throw new AbortException("Liquibase failed due to errors.");
        }
    }

    protected abstract void addCommandAndArguments(ArgumentListBuilder cliCommand, Properties configProperties, Run<?, ?> build, EnvVars environment, TaskListener listener) throws IOException;

    private boolean didErrorsOccur(Run<?, ?> build, int exitStatus) throws IOException {
        boolean result = true;
        if (exitStatus != 0) {
            result = false;
        }
        return result;
    }

    public String getChangeLogFile() {
        return changeLogFile;
    }

    @DataBoundSetter
    public void setChangeLogFile(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setUrl(String url) {
        this.url = url;
    }

    public String getContexts() {
        return contexts;
    }

    @DataBoundSetter
    public void setContexts(String contexts) {
        this.contexts = contexts;
    }

    public String getLiquibasePropertiesPath() {
        return liquibasePropertiesPath;
    }

    @DataBoundSetter
    public void setLiquibasePropertiesPath(String liquibasePropertiesPath) {
        this.liquibasePropertiesPath = liquibasePropertiesPath;
    }

    public String getChangeLogParameters() {
        return changeLogParameters;
    }

    @DataBoundSetter
    public void setChangeLogParameters(String changeLogParameters) {
        this.changeLogParameters = changeLogParameters;
    }

    public String getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getResourceDirectories() {
        return resourceDirectories;
    }

    @DataBoundSetter
    public void setResourceDirectories(String resourceDirectories) {
        this.resourceDirectories = resourceDirectories;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    @DataBoundSetter
    public void setPassword(String password) {
        this.password = password;
    }

    @Deprecated
    public String getUsername() {
        return username;
    }
    @Deprecated
    public String getPassword() {
        return password;
    }

    public void clearLegacyCredentials() {
        username=null;
        password=null;
    }

    public boolean hasLegacyCredentials() {
        return !Strings.isNullOrEmpty(username);
    }

    protected String getProperty(Properties configProperties, LiquibaseProperty property) {
        return configProperties.getProperty(property.propertyName());
    }

    protected void addArgument(ArgumentListBuilder cliCommand, String key, String value) {
        if (value != null && !value.equals("")) {
            cliCommand.addKeyValuePair("--", key, value, false);
        }

    }

    protected void addArgument(ArgumentListBuilder cliCommand, String key, String value, boolean maskValue) {
        cliCommand.addKeyValuePair("--", key, value, maskValue);
    }

    protected void addGlobalArguments(ArgumentListBuilder cliCommand, Properties configProperties, Run<?, ?> build, EnvVars environment, TaskListener listener) throws IOException, InterruptedException {
        final String classpath = Util.replaceMacro(getResourceDirectories(), environment);
        if (classpath != null) {
            addArgument(cliCommand, "classpath", classpath.replaceAll("\\s*,\\s*", ";"));
        }
        addArgument(cliCommand, "defaultsFile", Util.replaceMacro(getLiquibasePropertiesPath(), environment));
        addArgument(cliCommand, "changeLogFile", Util.replaceMacro(getChangeLogFile(), environment));
        addArgument(cliCommand, "url", Util.replaceMacro(getUrl(), environment));
        addArgument(cliCommand, "username", getProperty(configProperties, LiquibaseProperty.USERNAME));
        addArgument(cliCommand, "password", getProperty(configProperties, LiquibaseProperty.PASSWORD), true);
        addArgument(cliCommand, "contexts", Util.replaceMacro(getContexts(), environment));
        addArgument(cliCommand, "labels", Util.replaceMacro(getLabels(), environment));
    }

}
