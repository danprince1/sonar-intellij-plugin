package org.intellij.sonar.configuration.project;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.intellij.sonar.configuration.IncrementalScriptConfigurable;
import org.intellij.sonar.configuration.ResourcesSelectionConfigurable;
import org.intellij.sonar.configuration.SonarServerConfigurable;
import org.intellij.sonar.configuration.check.*;
import org.intellij.sonar.persistence.*;
import org.intellij.sonar.sonarserver.SonarServer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.wsclient.services.Resource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

import static org.intellij.sonar.util.MessagesUtil.errorMessage;
import static org.intellij.sonar.util.MessagesUtil.warnMessage;


public class ProjectSettingsConfigurable implements Configurable, ProjectComponent {

  public static final int SOURCE_CODE_ENTRY_MAX_LENGTH = 100;
  private static final ColumnInfo<IncrementalScriptBean, String> SCRIPT_COLUMN = new ColumnInfo<IncrementalScriptBean, String>("Script") {

    @Nullable
    @Override
    public String valueOf(IncrementalScriptBean incrementalScriptBean) {
      final String sourceCodeOfScript = incrementalScriptBean.getSourceCodeOfScript();
      if (!StringUtil.isEmptyOrSpaces(sourceCodeOfScript) && sourceCodeOfScript.length() >= SOURCE_CODE_ENTRY_MAX_LENGTH) {
        return sourceCodeOfScript.substring(0, SOURCE_CODE_ENTRY_MAX_LENGTH) + "...";
      } else {
        return sourceCodeOfScript;
      }
    }

  };
  private static final Logger LOG = Logger.getInstance(ProjectSettingsConfigurable.class);
  private static final String NO_SONAR = "<NO SONAR>";
  private static final ColumnInfo<Resource, String> TYPE_COLUMN = new ColumnInfo<Resource, String>("Type") {
    @Nullable
    @Override
    public String valueOf(Resource sonarResource) {
      if (Resource.QUALIFIER_PROJECT.equals(sonarResource.getQualifier())) {
        return "Project";
      } else if (Resource.QUALIFIER_MODULE.equals(sonarResource.getQualifier())) {
        return "Module";
      } else {
        return sonarResource.getQualifier();
      }
    }

  };
  private static final ColumnInfo<Resource, String> NAME_COLUMN = new ColumnInfo<Resource, String>("Name") {
    @Nullable
    @Override
    public String valueOf(Resource sonarResource) {
      return sonarResource.getName();
    }

    @Override
    public int getWidth(JTable table) {
      return 300;
    }

  };
  private static final ColumnInfo<Resource, String> KEY_COLUMN = new ColumnInfo<Resource, String>("Key") {

    @Nullable
    @Override
    public String valueOf(Resource sonarResource) {
      return sonarResource.getKey();
    }

  };
  private final TableView<Resource> mySonarResourcesTable;
  private final TableView<IncrementalScriptBean> myIncrementalAnalysisScriptsTable;
  private final ProjectSettingsComponent myProjectSettingsComponent;
  private Project myProject;
  private JButton myTestConfigurationButton;
  private JPanel myRootJPanel;
  private JPanel myPanelForSonarResources;
  private JPanel myPanelForIncrementalAnalysisScripts;
  private JComboBox mySonarServersComboBox;
  private JButton myAddSonarServerButton;
  private JButton myEditSonarServerButton;
  private JButton myRemoveSonarServerButton;

  public ProjectSettingsConfigurable(Project project) {
    this.myProject = project;
    this.mySonarResourcesTable = new TableView<Resource>();
    this.myIncrementalAnalysisScriptsTable = new TableView<IncrementalScriptBean>();
    this.myProjectSettingsComponent = myProject.getComponent(ProjectSettingsComponent.class);
  }

  private JComponent createSonarResourcesTable() {
    JPanel panelForTable = ToolbarDecorator.createDecorator(mySonarResourcesTable, null).
        setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            final String selectedSonarServerName = mySonarServersComboBox.getSelectedItem().toString();
            if (!NO_SONAR.equals(selectedSonarServerName)) {
              ResourcesSelectionConfigurable dlg = new ResourcesSelectionConfigurable(myProject, selectedSonarServerName);
              dlg.show();
              if (dlg.isOK()) {
                final java.util.List<Resource> selectedSonarResources = dlg.getSelectedSonarResources();
                final java.util.List<Resource> currentSonarResources = getCurrentSonarResources();

                Set<Resource> mergedSonarResourcesAsSet = new TreeSet<Resource>(new Comparator<Resource>() {
                  @Override
                  public int compare(Resource resource, Resource resource2) {
                    return resource.getKey().compareTo(resource2.getKey());
                  }
                });
                mergedSonarResourcesAsSet.addAll(currentSonarResources);
                mergedSonarResourcesAsSet.addAll(selectedSonarResources);

                setModelForSonarResourcesTable(Lists.newArrayList(mergedSonarResourcesAsSet));
              }
            }
          }
        }).
        setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            TableUtil.removeSelectedItems(mySonarResourcesTable);
          }
        })
        .disableUpDownActions().
            createPanel();
    panelForTable.setPreferredSize(new Dimension(-1, 100));
    return panelForTable;
  }

  private void setModelForSonarResourcesTable(List<Resource> sonarResources) {
    mySonarResourcesTable.setModelAndUpdateColumns(new ListTableModel<Resource>(new ColumnInfo[]{NAME_COLUMN, KEY_COLUMN, TYPE_COLUMN}, sonarResources, 0));
  }

  private java.util.List<Resource> getCurrentSonarResources() {
    return mySonarResourcesTable.getListTableModel().getItems();
  }

  private JComponent createIncrementalAnalysisScriptsTable() {
    JPanel panelForTable = ToolbarDecorator.createDecorator(myIncrementalAnalysisScriptsTable, null)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            IncrementalScriptConfigurable dlg = new IncrementalScriptConfigurable(myProject);
            dlg.show();
            if (dlg.isOK()) {
              final List<IncrementalScriptBean> incrementalScriptBeans = Lists.newArrayList(
                  ImmutableList.<IncrementalScriptBean>builder()
                      .addAll(myIncrementalAnalysisScriptsTable.getListTableModel().getItems())
                      .add(dlg.getIncrementalScriptBean())
                      .build()
              );
              setModelForIncrementalAnalysisScriptsTable(incrementalScriptBeans);
            }
          }
        })
        .setEditAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            IncrementalScriptConfigurable dlg = new IncrementalScriptConfigurable(myProject);
            dlg.setValuesFrom(myIncrementalAnalysisScriptsTable.getSelectedObject());
            dlg.show();
            if (dlg.isOK()) {
              final IncrementalScriptBean newIncrementalScriptBean = dlg.getIncrementalScriptBean();
              final IncrementalScriptBean selectedIncrementalScriptBean = myIncrementalAnalysisScriptsTable.getSelectedObject();

              final ArrayList<IncrementalScriptBean> incrementalScriptBeans = Lists.newArrayList(ImmutableList.<IncrementalScriptBean>builder()
                  .addAll(
                      FluentIterable.from(myIncrementalAnalysisScriptsTable.getListTableModel().getItems())
                          .filter(new Predicate<IncrementalScriptBean>() {
                            @Override
                            public boolean apply(IncrementalScriptBean it) {
                              return !it.equals(selectedIncrementalScriptBean);
                            }
                          })
                          .toList()
                  )
                  .add(newIncrementalScriptBean)
                  .build());

              setModelForIncrementalAnalysisScriptsTable(incrementalScriptBeans);
            }
          }
        })
        .disableUpDownActions()
        .setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            TableUtil.removeSelectedItems(myIncrementalAnalysisScriptsTable);
          }
        })
        .createPanel();
    panelForTable.setPreferredSize(new Dimension(-1, 100));
    return panelForTable;
  }

  private void setModelForIncrementalAnalysisScriptsTable(List<IncrementalScriptBean> incrementalScriptBeans) {
    myIncrementalAnalysisScriptsTable.setModelAndUpdateColumns(
        new ListTableModel<IncrementalScriptBean>(
            new ColumnInfo[]{SCRIPT_COLUMN},
            incrementalScriptBeans,
            0)
    );
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "SonarQube";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanelForSonarResources.setLayout(new BorderLayout());
    myPanelForSonarResources.add(createSonarResourcesTable(), BorderLayout.CENTER);
    myPanelForIncrementalAnalysisScripts.setLayout(new BorderLayout());
    myPanelForIncrementalAnalysisScripts.add(createIncrementalAnalysisScriptsTable(), BorderLayout.CENTER);

    addActionListenersForSonarServerButtons();
    initSonarServersComboBox();
    disableEditAndRemoveButtonsIfNoSonarSelected(mySonarServersComboBox);

    addActionListenerForTestConfigurationButton();
    return myRootJPanel;
  }

  private void initSonarServersComboBox() {
    Optional<Collection<SonarServerConfigurationBean>> sonarServerConfigurationBeans = SonarServersService.getAll();
    if (sonarServerConfigurationBeans.isPresent()) {
      mySonarServersComboBox.removeAllItems();
      mySonarServersComboBox.addItem(makeObj(NO_SONAR));
      for (SonarServerConfigurationBean sonarServerConfigurationBean : sonarServerConfigurationBeans.get()) {
        mySonarServersComboBox.addItem(makeObj(sonarServerConfigurationBean.getName()));
      }
    }
  }

  private Object makeObj(final String item) {
    return new Object() {
      public String toString() {
        return item;
      }
    };
  }

  private void addActionListenersForSonarServerButtons() {

    final JComboBox sonarServersComboBox = mySonarServersComboBox;

    sonarServersComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        disableEditAndRemoveButtonsIfNoSonarSelected(sonarServersComboBox);
      }
    });

    myAddSonarServerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {

        final SonarServerConfigurable dlg = showSonarServerConfigurableDialog();
        if (dlg.isOK()) {
          SonarServerConfigurationBean newSonarConfigurationBean = dlg.toSonarServerConfigurationBean();
          try {
            SonarServersService.add(newSonarConfigurationBean);
            mySonarServersComboBox.addItem(makeObj(newSonarConfigurationBean.getName()));
            selectItemForSonarServersComboBoxByName(newSonarConfigurationBean.getName());
          } catch (IllegalArgumentException e) {
            Messages.showErrorDialog(newSonarConfigurationBean.getName() + " already exists", "Sonar Name Error");
            showSonarServerConfigurableDialog(newSonarConfigurationBean);
          }
        }
      }
    });

    myEditSonarServerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        final Object selectedSonarServer = sonarServersComboBox.getSelectedItem();
        final Optional<SonarServerConfigurationBean> oldBean = SonarServersService.get(selectedSonarServer.toString());
        if (!oldBean.isPresent()) {
          Messages.showErrorDialog(selectedSonarServer.toString() + " is not more preset", "Cannot Perform Edit");
        } else {
          final SonarServerConfigurable dlg = showSonarServerConfigurableDialog(oldBean.get());
          if (dlg.isOK()) {
            SonarServerConfigurationBean newSonarConfigurationBean = dlg.toSonarServerConfigurationBean();
            try {
              SonarServersService.remove(oldBean.get().getName());
              SonarServersService.add(newSonarConfigurationBean);
              mySonarServersComboBox.removeItem(selectedSonarServer);
              mySonarServersComboBox.addItem(makeObj(newSonarConfigurationBean.getName()));
              selectItemForSonarServersComboBoxByName(newSonarConfigurationBean.getName());
            } catch (IllegalArgumentException e) {
              Messages.showErrorDialog(selectedSonarServer.toString() + " cannot be saved\n\n" + Throwables.getStackTraceAsString(e), "Cannot Perform Edit");
            }
          }
        }
      }
    });

    myRemoveSonarServerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        final Object selectedSonarServer = sonarServersComboBox.getSelectedItem();
        int rc = Messages.showOkCancelDialog("Are you sure you want to remove " + selectedSonarServer.toString() + " ?", "Remove Sonar Server", AllIcons.Actions.Help);
        if (rc == Messages.OK) {
          SonarServersService.remove(selectedSonarServer.toString());
          mySonarServersComboBox.removeItem(selectedSonarServer);
          disableEditAndRemoveButtonsIfNoSonarSelected(mySonarServersComboBox);
        }
      }
    });
  }

  private void disableEditAndRemoveButtonsIfNoSonarSelected(JComboBox sonarServersComboBox) {
    final boolean isNoSonarSelected = NO_SONAR.equals(sonarServersComboBox.getSelectedItem().toString());
    myEditSonarServerButton.setEnabled(!isNoSonarSelected);
    myRemoveSonarServerButton.setEnabled(!isNoSonarSelected);
  }

  private void selectItemForSonarServersComboBoxByName(String name) {
    Optional itemToSelect = Optional.absent();
    for (int i = 0; i < mySonarServersComboBox.getItemCount(); i++) {
      final Object item = mySonarServersComboBox.getItemAt(i);
      if (name.equals(item.toString())) {
        itemToSelect = Optional.of(item);
      }
    }
    if (itemToSelect.isPresent())
      mySonarServersComboBox.setSelectedItem(itemToSelect.get());
  }

  private SonarServerConfigurable showSonarServerConfigurableDialog() {
    return showSonarServerConfigurableDialog(null);
  }

  private SonarServerConfigurable showSonarServerConfigurableDialog(SonarServerConfigurationBean oldSonarServerConfigurationBean) {
    final SonarServerConfigurable dlg = new SonarServerConfigurable(myProject);
    if (null != oldSonarServerConfigurationBean)
      dlg.setValuesFrom(oldSonarServerConfigurationBean);
    dlg.show();
    return dlg;
  }

  @Override
  public boolean isModified() {
    if (null == myProjectSettingsComponent) return false;
    ProjectSettingsBean state = myProjectSettingsComponent.getState();
    return null == state || !state.equals(this.toProjectSettingsBean());
  }

  @Override
  public void apply() throws ConfigurationException {
    myProjectSettingsComponent.loadState(this.toProjectSettingsBean());
  }

  @Override
  public void reset() {
    if (myProjectSettingsComponent != null && myProjectSettingsComponent.getState() != null) {
      ProjectSettingsBean persistedState = myProjectSettingsComponent.getState();
      this.setValuesFromProjectSettingsBean(persistedState);
    }
  }

  @Override
  public void disposeUIResources() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void projectOpened() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void projectClosed() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void initComponent() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void disposeComponent() {
    // To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarQube";
  }

  public ProjectSettingsBean toProjectSettingsBean() {

    ProjectSettingsBean projectSettingsBean = new ProjectSettingsBean();
    projectSettingsBean.setSonarServerName(mySonarServersComboBox.getSelectedItem().toString());
    projectSettingsBean.setResources(ImmutableList.copyOf(getCurrentSonarResources()));
    projectSettingsBean.setScripts(ImmutableList.copyOf(myIncrementalAnalysisScriptsTable.getItems()));

    return projectSettingsBean;
  }

  public void setValuesFromProjectSettingsBean(ProjectSettingsBean projectSettingsBean) {

    if (null == projectSettingsBean) return;
    selectItemForSonarServersComboBoxByName(projectSettingsBean.getSonarServerName());

    final ArrayList<Resource> resources = Lists.newArrayList(projectSettingsBean.getResources());
    setModelForSonarResourcesTable(resources);

    final ArrayList<IncrementalScriptBean> scripts = Lists.newArrayList(projectSettingsBean.getScripts());
    setModelForIncrementalAnalysisScriptsTable(scripts);
  }

  private void addActionListenerForTestConfigurationButton() {
    myTestConfigurationButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {

        StringBuilder testResultMessageBuilder = new StringBuilder();

        final String selectedSonarServerName = mySonarServersComboBox.getSelectedItem().toString();
        if (NO_SONAR.equals(selectedSonarServerName)) {
          testResultMessageBuilder.append(warnMessage("No sonar server selected\n"));
        } else {
          final Optional<SonarServerConfigurationBean> sonarServerConfiguration = SonarServersService.get(selectedSonarServerName);

          if (!sonarServerConfiguration.isPresent()) {
            testResultMessageBuilder.append(String.format("Cannot find configuration for %s\n", selectedSonarServerName));
          } else {
            final SonarServer sonarServer = SonarServer.create(sonarServerConfiguration.get());

            final ConnectionCheck connectionCheck = testSonarServerConnection(sonarServer);
            testResultMessageBuilder.append(connectionCheck.getMessage());
            if (connectionCheck.isOk()) {
              testResultMessageBuilder
                  .append(testGetRules(sonarServer))
                  .append(testGetIssues(sonarServer));
            }
          }
        }

        testResultMessageBuilder.append(testSourceDirectoryPaths())
            .append(testSonarReportFiles())
            .append(testScriptsExecution());

        Messages.showMessageDialog(testResultMessageBuilder.toString(), "Configuration Check Result", AllIcons.Actions.IntentionBulb);

      }
    });
  }

  private String testScriptsExecution() {
    StringBuilder sb = new StringBuilder();
    for (IncrementalScriptBean incrementalScriptBean : myIncrementalAnalysisScriptsTable.getItems()) {
      ScriptExecutionCheck scriptExecutionCheck = new ScriptExecutionCheck(
          incrementalScriptBean, new File(myProject.getBaseDir().getPath()));
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
          scriptExecutionCheck,
          "Testing Script Execution", true, myProject
      );
      sb.append(scriptExecutionCheck.getMessage());
    }

    return sb.toString();
  }


  private String testSourceDirectoryPaths() {
    StringBuilder sb = new StringBuilder();
    for (IncrementalScriptBean incrementalScriptBean : myIncrementalAnalysisScriptsTable.getItems()) {
      for (String sourceDirectoryPath : incrementalScriptBean.getSourcePaths()) {
        FileExistenceCheck fileExistenceCheck = new FileExistenceCheck(sourceDirectoryPath);
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            fileExistenceCheck,
            "Testing File Existence", true, myProject
        );
        sb.append(fileExistenceCheck.getMessage());
      }
    }
    return sb.toString();
  }

  private String testSonarReportFiles() {
    StringBuilder sb = new StringBuilder();
    for (IncrementalScriptBean incrementalScriptBean : myIncrementalAnalysisScriptsTable.getItems()) {
      FileExistenceCheck fileExistenceCheck = new FileExistenceCheck(incrementalScriptBean.getPathToSonarReport());
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
          fileExistenceCheck,
          "Testing File Existence", true, myProject
      );
      sb.append(fileExistenceCheck.getMessage());
      if (fileExistenceCheck.isOk()) {
        SonarReportContentCheck sonarReportContentCheck = new SonarReportContentCheck(incrementalScriptBean.getPathToSonarReport());
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            sonarReportContentCheck,
            "Testing Sonar Report Contents", true, myProject
        );
        sb.append(sonarReportContentCheck.getMessage());
      }
    }
    return sb.toString();
  }

  private String testGetIssues(SonarServer sonarServer) {
    final List<Resource> resources = mySonarResourcesTable.getItems();
    if (null == resources || resources.size() == 0) return "";

    StringBuilder sb = new StringBuilder();
    for (Resource resource : resources) {
      final String resourceKey = resource.getKey();
      final IssuesRetrievalCheck issuesRetrievalCheck = new IssuesRetrievalCheck(sonarServer, resourceKey);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
          issuesRetrievalCheck,
          "Testing Issues", true, myProject
      );
      sb.append(issuesRetrievalCheck.getMessage());
    }

    return sb.toString();
  }

  private String testGetRules(SonarServer sonarServer) {

    final List<Resource> resources = mySonarResourcesTable.getItems();
    if (null == resources || resources.size() == 0)
      return errorMessage("No sonar resource configured");

    StringBuilder sb = new StringBuilder();
    for (Resource resource : resources) {
      final String resourceKey = resource.getKey();
      final RulesRetrievalCheck rulesRetrievalCheck = new RulesRetrievalCheck(sonarServer, resourceKey);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
          rulesRetrievalCheck,
          "Testing Rules", true, myProject
      );
      sb.append(rulesRetrievalCheck.getMessage());
    }

    return sb.toString();
  }

  private ConnectionCheck testSonarServerConnection(SonarServer sonarServer) {

    final ConnectionCheck connectionCheck = new ConnectionCheck(sonarServer);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
        connectionCheck,
        "Testing Connection", true, myProject
    );

    return connectionCheck;
  }

}