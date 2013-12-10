/*
 * Copyright 2012 Krzysztof Otrebski (krzysztof.otrebski@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package pl.otros.vfs.browser;

import net.miginfocom.swing.MigLayout;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.DataConfiguration;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.otros.vfs.browser.actions.AddCurrentLocationToFavoriteAction;
import pl.otros.vfs.browser.actions.BaseNavigateAction;
import pl.otros.vfs.browser.actions.EditFavorite;
import pl.otros.vfs.browser.actions.OpenSelectedFavorite;
import pl.otros.vfs.browser.favorit.Favorite;
import pl.otros.vfs.browser.favorit.FavoritesUtils;
import pl.otros.vfs.browser.i18n.Messages;
import pl.otros.vfs.browser.list.MutableListDragListener;
import pl.otros.vfs.browser.list.MutableListDropHandler;
import pl.otros.vfs.browser.list.MutableListModel;
import pl.otros.vfs.browser.list.SelectFirstElementFocusAdapter;
import pl.otros.vfs.browser.preview.PreviewComponent;
import pl.otros.vfs.browser.preview.PreviewListener;
import pl.otros.vfs.browser.table.*;
import pl.otros.vfs.browser.util.GuiUtils;
import pl.otros.vfs.browser.util.SwingUtils;
import pl.otros.vfs.browser.util.VFSUtils;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class VfsBrowser extends JPanel {


  public static final String MULTI_SELECTION_ENABLED_CHANGED_PROPERTY = "MultiSelectionEnabledChangedProperty";
  public static final String MULTI_SELECTION_MODE_CHANGED_PROPERTY = "SelectionModeChangedProperty";

  private static final Logger LOGGER = LoggerFactory.getLogger(VfsBrowser.class);
  private static final Icon COMPUTER_ICON = Icons.getInstance().getComputer();

  private static final String ACTION_GO_UP = "GO_UP";
  private static final String ACTION_OPEN = "OPEN";
  private static final String ACTION_DELETE = "DELETE";
  private static final String ACTION_APPROVE = "ACTION APPROVE";
  private static final String ACTION_FOCUS_ON_TABLE = "FOCUS ON TABLE";

  private static final String ACTION_EDIT = "EDIT";
  private static final String TABLE = "TABLE";
  private static final String LOADING = "LOADING";
  protected JTextField pathField;
  protected JTable tableFiles;
  protected JScrollPane tableScrollPane;
  protected JList favoritesUserList;
  protected VfsTableModel vfsTableModel;

  protected JPanel tablePanel;

  private PreviewComponent previewComponent;

  private JCheckBox showHidCheckBox;
  private JButton goUpButton;

  private JLabel statusLabel;

  private FileObject currentLocation;
  private CardLayout cardLayout;


  private MutableListModel<Favorite> favoritesUserListModel;
  private SelectionMode selectionMode = SelectionMode.DIRS_AND_FILES;
  private Action actionApproveDelegate;
  private Action actionCancelDelegate;

  private boolean multiSelectionEnabled = false;
  private JButton actionApproveButton;
  private JButton actionCancelButton;
  private DataConfiguration configuration;
  private JProgressBar loadingProgressBar;
  private JLabel loadingIconLabel;
  private TaskContext taskContext;
  private JToggleButton skipCheckingLinksButton;
  private JTextField filterField;
  private TableRowSorter<VfsTableModel> sorter;

  private boolean showHidden = true;

  public VfsBrowser() {
    this(new BaseConfiguration());
  }

  public VfsBrowser(Configuration configuration) {
    this(configuration, null);
  }

  public VfsBrowser(Configuration configuration, final String initialPath) {
    super();
    this.configuration = new DataConfiguration(configuration);


    initGui(initialPath);
    VFSUtils.loadAuthStore();

  }


  public void goToUrl(String url) {
    LOGGER.info("Going to URL: " + url);
    try {
      FileObject resolveFile = VFSUtils.resolveFileObject(url);
      String type = "?";
      if (resolveFile != null) {
        type = resolveFile.getType().toString();
      }
      LOGGER.info("URL: " + url + " is resolved " + type);
      goToUrl(resolveFile);
    } catch (FileSystemException e) {
      LOGGER.error("Can't go to URL " + url, e);
      final String message = ExceptionsUtils.getRootCause(e).getClass().getName() + ": " + ExceptionsUtils.getRootCause(e).getLocalizedMessage();

      Runnable runnable = new Runnable() {
        public void run() {
          JOptionPane.showMessageDialog(VfsBrowser.this, "Can't open location: " + message);
        }
      };
      SwingUtils.runInEdt(runnable);
    }
  }

  public void goToUrl(final FileObject fileObject) {

    if (taskContext != null) {
      taskContext.setStop(true);
    }

    final FileObject[] files = VFSUtils.getFiles(fileObject);
    LOGGER.info("Have {} files in {}", files.length, fileObject.getName().getFriendlyURI());
    this.currentLocation = fileObject;

    taskContext = new TaskContext(Messages.getMessage("browser.checkingSFtpLinksTask"), files.length);
    taskContext.setIndeterminate(false);
    SwingWorker<Void, Void> refreshWorker = new SwingWorker<Void, Void>() {
      int icon = 0;
      Icon[] icons = new Icon[]{Icons.getInstance().getNetworkStatusOnline(), Icons.getInstance().getNetworkStatusAway(), Icons.getInstance().getNetworkStatusOffline()};


      @Override
      protected void process(List<Void> chunks) {
        loadingProgressBar.setIndeterminate(taskContext.isIndeterminate());
        loadingProgressBar.setMaximum(taskContext.getMax());
        loadingProgressBar.setValue(taskContext.getCurrentProgress());
        loadingProgressBar.setString(String.format("%s [%d of %d]", taskContext.getName(), taskContext.getCurrentProgress(), taskContext.getMax()));
        loadingIconLabel.setIcon(icons[++icon % icons.length]);
      }

      @Override
      protected Void doInBackground() throws Exception {
        try {
          while (!taskContext.isStop()) {
            publish();
            Thread.sleep(300);
          }
        } catch (InterruptedException ignore) {
          //ignore
        }
        return null;
      }
    };
    new Thread(refreshWorker).start();


    if (!skipCheckingLinksButton.isSelected()) {
      VFSUtils.checkForSftpLinks(files, taskContext);
    }
    taskContext.setStop(true);
    // Subtract hidden files

    //TODO remove this
    /**
    FileObject[] visibleFiles = files;
    if (!showHidCheckBox.isSelected() || filterTextBuffer.length() > 0) {
        Pattern revealPattern = null;
        if (filterTextBuffer.length() > 0) {
            if (filterTextBuffer.charAt(0) == '/') try {
                revealPattern = Pattern.compile(filterTextBuffer.substring(1));
            } catch (PatternSyntaxException pse) {
                LOGGER.error(pse.getMessage());
                // TODO:  Queue up filterField.setBackground and .requestFocus
            } else {
                revealPattern = Pattern.compile("\\Q" + filterTextBuffer
                        .toString()
                        .replaceAll("\\[[^]]+\\]", "\\\\E$0\\\\Q")
                        .replaceAll("\\?", "\\\\E.\\\\Q")
                        .replaceAll("\\*", "\\\\E.*\\\\Q"));
            }
            LOGGER.debug(String.format("revealPattern=(%s)", revealPattern));
        }
        else {
          revealPattern = Pattern.compile("\\Q" + filterTextBuffer
              .toString()
              .replaceAll("\\[[^]]+\\]", "\\\\E$0\\\\Q")
              .replaceAll("\\?", "\\\\E.\\\\Q")
              .replaceAll("\\*", "\\\\E.*\\\\Q"));
        }
        LOGGER.debug(String.format("revealPattern=(%s)", revealPattern));
      }
      List<FileObject> revealedFiles = new ArrayList<FileObject>();
      for (FileObject fo : files)
        try {
          String baseName = fo.getName().getBaseName();
          if (!showHidCheckBox.isSelected()
              && (fo.isHidden() || baseName.startsWith("."))) continue;
          if (revealPattern != null
              && !revealPattern.matcher(baseName).matches()) continue;
          revealedFiles.add(fo);
        } catch (FileSystemException fse) {
          LOGGER.error(String.format(
              "Failed to get name from file object '%s'", fo), fse);
        }
      visibleFiles = revealedFiles.toArray(new FileObject[0]);
    }
    **/
    final int filesCount = files.length;
    final FileObject[] fileObjectsWithParent = addParentToFiles(files);
    Runnable r = new Runnable() {

      @Override
      public void run() {
        vfsTableModel.setContent(fileObjectsWithParent);
        try {
          pathField.setText(fileObject.getURL().toString());
        } catch (FileSystemException e) {
          LOGGER.error("Can't get URL", e);
        }
        statusLabel.setText(Messages.getMessage("browser.folderContainsXElements", filesCount));
        if (tableFiles.getRowCount() > 0) {
          tableFiles.getSelectionModel().setSelectionInterval(0, 0);
        }
      }
    };
    SwingUtils.runInEdt(r);
  }

  private FileObject[] addParentToFiles(FileObject[] files) {
    FileObject[] newFiles = new FileObject[files.length + 1];
    try {
      FileObject parent = currentLocation.getParent();
      if (parent != null) {
        newFiles[0] = new ParentFileObject(parent);
        System.arraycopy(files, 0, newFiles, 1, files.length);
      } else {
        newFiles = files;
      }
    } catch (FileSystemException e) {
      LOGGER.warn("Can't add parent", e);
      newFiles = files;
    }
    return newFiles;
  }





  private void initGui(final String initialPath) {
    this.setLayout(new BorderLayout());
    JLabel pathLabel = new JLabel(Messages.getMessage("nav.path"));
    pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
    pathField = new JTextField(80);
    pathField.setFont(pathLabel.getFont().deriveFont(pathLabel.getFont().getSize() * 1.2f));
    pathField.setToolTipText(Messages.getMessage("nav.pathTooltip"));
    GuiUtils.addBlinkOnFocusGain(pathField);

    InputMap inputMapPath = pathField.getInputMap(JComponent.WHEN_FOCUSED);
    inputMapPath.put(KeyStroke.getKeyStroke("ENTER"), "OPEN_PATH");
    inputMapPath.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_FOCUS_ON_TABLE);
    pathField.getActionMap().put("OPEN_PATH", new BaseNavigateAction(this) {

      @Override
      protected void performLongOperation(CheckBeforeActionResult actionResult) {
        goToUrl(pathField.getText().trim());
      }

      @Override
      protected boolean canGoUrl() {
        return true;
      }

      @Override
      protected boolean canExecuteDefaultAction() {
        return false;
      }

    });
    pathField.getActionMap().put(ACTION_FOCUS_ON_TABLE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        tableFiles.requestFocusInWindow();
        if (tableFiles.getSelectedRow() < 0 && tableFiles.getRowCount() == 0) {
          tableFiles.getSelectionModel().setSelectionInterval(0, 0);
        }
      }
    });

    goUpButton = new JButton(new BaseNavigateActionGoUp(this));

    JButton refreshButton = new JButton(new BaseNavigateActionRefresh(this));

    JToolBar upperPanel = new JToolBar(Messages.getMessage("nav.ToolBarName"));
    upperPanel.setRollover(true);
    upperPanel.add(pathLabel);
    upperPanel.add(pathField, "growx");
    upperPanel.add(goUpButton);
    upperPanel.add(refreshButton);

    JButton addCurrentLocationToFavoriteButton = new JButton(new AddCurrentLocationToFavoriteAction(this));
    addCurrentLocationToFavoriteButton.setText("");
    upperPanel.add(addCurrentLocationToFavoriteButton);

    previewComponent = new PreviewComponent();

    vfsTableModel = new VfsTableModel();

    tableFiles = new JTable(vfsTableModel);
    tableFiles.setFillsViewportHeight(true);
    tableFiles.getColumnModel().getColumn(0).setMinWidth(140);
    tableFiles.getColumnModel().getColumn(1).setMaxWidth(80);
    tableFiles.getColumnModel().getColumn(2).setMaxWidth(80);
    tableFiles.getColumnModel().getColumn(3).setMaxWidth(180);
    tableFiles.getColumnModel().getColumn(3).setMinWidth(120);

    // create the layer for the panel using our custom layerUI
    tableScrollPane = new JScrollPane(tableFiles);
    JSplitPane tableWithPreviewPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, tableScrollPane, previewComponent);
    tableWithPreviewPane.setOneTouchExpandable(true);

    sorter = new TableRowSorter<VfsTableModel>(vfsTableModel);
    final FileNameWithTypeComparator fileNameWithTypeComparator = new FileNameWithTypeComparator();
    sorter.addRowSorterListener(new RowSorterListener() {
      @Override
      public void sorterChanged(RowSorterEvent e) {
        RowSorterEvent.Type type = e.getType();
        if (type.equals(RowSorterEvent.Type.SORT_ORDER_CHANGED)) {
          List<? extends RowSorter.SortKey> sortKeys = e.getSource().getSortKeys();
          for (RowSorter.SortKey sortKey : sortKeys) {
            if (sortKey.getColumn() == VfsTableModel.COLUMN_NAME) {
              fileNameWithTypeComparator.setSortOrder(sortKey.getSortOrder());
            }
          }
        }
      }
    });
    sorter.setComparator(VfsTableModel.COLUMN_NAME, fileNameWithTypeComparator);

    tableFiles.setRowSorter(sorter);
    tableFiles.setShowGrid(false);
    tableFiles.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        try {
          selectionChanged();
        } catch (FileSystemException e1) {
          LOGGER.error("Error during update state", e);
        }
      }
    });
    tableFiles.setColumnSelectionAllowed(false);

    tableFiles.setDefaultRenderer(FileSize.class, new FileSizeTableCellRenderer());
    tableFiles.setDefaultRenderer(FileNameWithType.class, new FileNameWithTypeTableCellRenderer());
    tableFiles.setDefaultRenderer(Date.class, new MixedDateTableCellRenderer());
    tableFiles.setDefaultRenderer(FileType.class, new FileTypeTableCellRenderer());

    tableFiles.getSelectionModel().addListSelectionListener(new PreviewListener(this, previewComponent));

    JPanel favoritesPanel = new JPanel(new MigLayout("wrap, fillx", "[grow]"));
    favoritesUserListModel = new MutableListModel<Favorite>();

    List<Favorite> favSystemLocations = FavoritesUtils.getSystemLocations();
    List<Favorite> favUser = FavoritesUtils.loadFromProperties(configuration);
    List<Favorite> favJVfsFileChooser = FavoritesUtils.getJvfsFileChooserBookmarks();
    for (Favorite favorite : favUser) {
      favoritesUserListModel.add(favorite);
    }
    favoritesUserListModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        saveFavorites();

      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        saveFavorites();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        saveFavorites();
      }

      protected void saveFavorites() {
        FavoritesUtils.storeFavorites(configuration, favoritesUserListModel.getList());
      }
    });


    favoritesUserList = new JList(favoritesUserListModel);
    favoritesUserList.setTransferHandler(new MutableListDropHandler(favoritesUserList));
    new MutableListDragListener(favoritesUserList);
    favoritesUserList.setCellRenderer(new FavoriteListCellRenderer());
    favoritesUserList.addFocusListener(new SelectFirstElementFocusAdapter());

    addOpenActionToList(favoritesUserList);
    addEditActionToList(favoritesUserList, favoritesUserListModel);

    favoritesUserList.getActionMap().put(ACTION_DELETE, new AbstractAction(Messages.getMessage("favorites.deleteButtonText"),
        Icons.getInstance().getMinusButton()) {

      @Override
      public void actionPerformed(ActionEvent e) {
        Favorite favorite = favoritesUserListModel.getElementAt(favoritesUserList.getSelectedIndex());
        if (!Favorite.Type.USER.equals(favorite.getType())) {
          return;
        }
        int response = JOptionPane.showConfirmDialog(VfsBrowser.this, Messages.getMessage("favorites.areYouSureToDeleteConnections"),
            Messages.getMessage("favorites.confirm"),
            JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
          favoritesUserListModel.remove(favoritesUserList.getSelectedIndex());
        }
      }
    });
    InputMap favoritesListInputMap = favoritesUserList.getInputMap(JComponent.WHEN_FOCUSED);
    favoritesListInputMap.put(KeyStroke.getKeyStroke("DELETE"), ACTION_DELETE);


    ActionMap actionMap = tableFiles.getActionMap();
    actionMap.put(ACTION_OPEN, new BaseNavigateActionOpen(this));
    actionMap.put(ACTION_GO_UP, new BaseNavigateActionGoUp(this));
    actionMap.put(ACTION_APPROVE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (actionApproveButton.isEnabled()) {
          actionApproveDelegate.actionPerformed(e);
        }
      }
    });

    InputMap inputMap = tableFiles.getInputMap(JComponent.WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke("ENTER"), ACTION_OPEN);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK), ACTION_APPROVE);

    inputMap.put(KeyStroke.getKeyStroke("BACK_SPACE"), ACTION_GO_UP);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_GO_UP);
    addPopupMenu(favoritesUserList, ACTION_OPEN, ACTION_EDIT, ACTION_DELETE);

    JList favoriteSystemList = new JList(new Vector<Object>(favSystemLocations));
    favoriteSystemList.setCellRenderer(new FavoriteListCellRenderer());
    addOpenActionToList(favoriteSystemList);
    addPopupMenu(favoriteSystemList, ACTION_OPEN);
    favoriteSystemList.addFocusListener(new SelectFirstElementFocusAdapter());

    JList favoriteJVfsList = new JList(new Vector<Object>(favJVfsFileChooser));
    addOpenActionToList(favoriteJVfsList);
    favoriteJVfsList.setCellRenderer(new FavoriteListCellRenderer());
    addPopupMenu(favoriteJVfsList, ACTION_OPEN);
    favoriteJVfsList.addFocusListener(new SelectFirstElementFocusAdapter());


    favoritesPanel.add(getTitleListLabel(Messages.getMessage("favorites.systemLocations"), COMPUTER_ICON), "gapleft 16");
    favoritesPanel.add(favoriteSystemList, "growx");
    favoritesPanel.add(getTitleListLabel(Messages.getMessage("favorites.favorites"), Icons.getInstance().getStar()), "gapleft 16");
    favoritesPanel.add(favoritesUserList, "growx");

    if (favoriteJVfsList.getModel().getSize() > 0) {
      favoritesPanel.add(getTitleListLabel(Messages.getMessage("favorites.JVfsFileChooserBookmarks"), null), "gapleft 16");
      favoritesPanel.add(favoriteJVfsList, "growx");
    }


    tableFiles.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          tableFiles.getActionMap().get(ACTION_OPEN).actionPerformed(null);
        }
      }
    });
    tableFiles.addKeyListener(new QuickSearchKeyAdapter());


    cardLayout = new CardLayout();
    tablePanel = new JPanel(cardLayout);
    loadingProgressBar = new JProgressBar();
    loadingProgressBar.setStringPainted(true);
    loadingProgressBar.setString(Messages.getMessage("browser.loading"));
    loadingProgressBar.setIndeterminate(true);
    loadingIconLabel = new JLabel(Icons.getInstance().getNetworkStatusOnline());
    skipCheckingLinksButton = new JToggleButton(Messages.getMessage("browser.skipCheckingLinks"));
    skipCheckingLinksButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (taskContext != null) {
          taskContext.setStop(skipCheckingLinksButton.isSelected());
        }
      }
    });
    JPanel loadingPanel = new JPanel(new MigLayout());
    loadingPanel.add(loadingIconLabel, "right");
    loadingPanel.add(loadingProgressBar, "left, w 420:420:500,wrap");
    loadingPanel.add(skipCheckingLinksButton, "span, right");
    tablePanel.add(loadingPanel, LOADING);
    tablePanel.add(tableWithPreviewPane, TABLE);


    showHidCheckBox = new JCheckBox(Messages.getMessage("browser.showHidden.label"), showHidden);
    showHidCheckBox.setToolTipText(Messages.getMessage("browser.showHidden.tooltip"));
    Font tmpFont = showHidCheckBox.getFont();
    showHidCheckBox.setFont(tmpFont.deriveFont(tmpFont.getSize() * 0.9f));
    showHidCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateUiFilters();
      }
    });

    final String defaultFilterText = Messages.getMessage("browser.filter.defaultText");
    filterField = new JTextField("", 16);
    filterField.setForeground(filterField.getDisabledTextColor());
    filterField.setToolTipText(Messages.getMessage("browser.filter.tooltip"));
    filterField.addFocusListener(new FocusListener() {
        public void focusGained(FocusEvent e) {
            assert e.getComponent() instanceof JTextField;
            JTextComponent fField = (JTextComponent) e.getComponent();
            if (filterTextBuffer.length() < 1) {
                fField.setText("");
                fField.setForeground(normalTextFieldColor);
            }
        }
        public void focusLost(FocusEvent e) {
            assert e.getComponent() instanceof JTextField;
            String fieldText = ((JTextComponent) e.getComponent()).getText();
            try {
                if (fieldText.equals(filterTextBuffer.toString())) return;
                filterTextBuffer.replace(
                        0, filterTextBuffer.length(), fieldText);
                refreshButtonRef.doClick();
            } finally {
                if (fieldText.equals("")) {
                    filterField.setText(defaultFilterText);
                    filterField.setForeground(filterField.getDisabledTextColor());
                }
            }
            LOGGER.debug(String.format(
                    "Filter field lost focus, filter text -> (%s)",
                    filterTextBuffer));
        }
    });
    filterField.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            assert e.getSource() instanceof JTextField;
            String fieldText = ((JTextComponent) e.getSource()).getText();
            if (fieldText.equals(filterTextBuffer.toString())) return;
            filterTextBuffer.replace(
                    0, filterTextBuffer.length(), fieldText);
            refreshButtonRef.doClick();
            LOGGER.debug(String.format(
                    "Filter field received ENTER, filter text -> (%s)",
                    filterTextBuffer));
        }
    });

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateUiFilters();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateUiFilters();
      }
    });
    sorter.setRowFilter(createFilter());
    showTable();
    statusLabel = new JLabel();
    JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(favoritesPanel), tablePanel);
    jSplitPane.setOneTouchExpandable(true);
    jSplitPane.setDividerLocation(180);

    actionApproveButton = new JButton(actionApproveDelegate);
    actionApproveButton.setFont(actionApproveButton.getFont().deriveFont(Font.BOLD));
    actionCancelButton = new JButton(actionCancelDelegate);

    JPanel southPanel = new JPanel(
            new MigLayout("", "[]30px[]30px[]push[][]", ""));
    southPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    southPanel.add(showHidCheckBox);
    southPanel.add(filterField);
    southPanel.add(statusLabel);
    southPanel.add(actionApproveButton);
    southPanel.add(actionCancelButton);

    this.add(upperPanel, BorderLayout.NORTH);
    this.add(jSplitPane, BorderLayout.CENTER);
    this.add(southPanel, BorderLayout.SOUTH);

    try {
      selectionChanged();
    } catch (FileSystemException e) {
      LOGGER.error("Can't initialize default selection mode", e);
    }
    try {
      if (initialPath == null) {
        goToUrl(VFSUtils.getUserHome());
      } else {
        goToUrl(initialPath);
      }
    } catch (FileSystemException e1) {
      LOGGER.error("Can't initialize default location", e1);
    }
  }

  private void updateUiFilters() {
    showHidden = showHidCheckBox.isSelected();
    sorter.setRowFilter(createFilter());
  }

  private RowFilter<VfsTableModel, Integer> createFilter() {
    RowFilter<VfsTableModel, Integer> regexFilter = new VfsTableModelFileNameRowFilter(filterField);
    RowFilter<VfsTableModel, Integer> hiddenFilter = new VfsTableModelHiddenFileRowFilter(showHidden);
    RowFilter<VfsTableModel, Integer> alwaysShowParent = new VfsTableModelShowParentRowFilter();
    RowFilter<VfsTableModel, Integer> filters = RowFilter.andFilter(Arrays.asList(regexFilter, hiddenFilter));
    filters = RowFilter.orFilter(Arrays.asList(filters,alwaysShowParent));
    return filters;
  }

  private JLabel getTitleListLabel(String text, Icon icon) {
    JLabel jLabel = new JLabel(text, icon, SwingConstants.CENTER);
    Font font = jLabel.getFont();
    jLabel.setFont(font.deriveFont(Font.ITALIC | Font.BOLD, font.getSize() * 1.1f));
    jLabel.setBorder(BorderFactory.createEmptyBorder(10, 3, 0, 3));

    return jLabel;
  }

  private JPopupMenu addPopupMenu(JList list, String... actions) {
    JPopupMenu favoritesPopupMenu = new JPopupMenu();
    for (String action : actions) {
      favoritesPopupMenu.add(list.getActionMap().get(action));
    }
    list.addKeyListener(new PopupListener(favoritesPopupMenu));
    list.addMouseListener(new PopupListener(favoritesPopupMenu));
    return favoritesPopupMenu;
  }

  private void addOpenActionToList(final JList favoritesList) {
    favoritesList.getActionMap().put(ACTION_OPEN, new OpenSelectedFavorite(this, favoritesList));
    favoritesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          favoritesList.getActionMap().get(ACTION_OPEN).actionPerformed(null);
        }
      }
    });
    InputMap favoritesListInputMap = favoritesList.getInputMap(JComponent.WHEN_FOCUSED);
    favoritesListInputMap.put(KeyStroke.getKeyStroke("ENTER"), ACTION_OPEN);
  }

  private void addEditActionToList(final JList favoritesList, final MutableListModel<Favorite> listModel) {
    favoritesList.getActionMap().put(ACTION_EDIT, new EditFavorite(favoritesList, listModel));

    InputMap favoritesListInputMap = favoritesList.getInputMap(JComponent.WHEN_FOCUSED);
    favoritesListInputMap.put(KeyStroke.getKeyStroke("F2"), ACTION_EDIT);
  }

  private void selectionChanged() throws FileSystemException {
    LOGGER.debug("Updating selection");
    boolean acceptEnabled = false;
    if (getSelectedFiles().length == 0) {
      acceptEnabled = false;
    } else if (isMultiSelectionEnabled()) {
      boolean filesSelected = false;
      boolean folderSelected = false;

      for (FileObject fo : getSelectedFiles()) {
        FileType fileType = fo.getType();
        if (fileType == FileType.FILE) {
          filesSelected = true;
        } else if (fileType == FileType.FOLDER) {
          folderSelected = true;
        }
      }
      if (selectionMode == SelectionMode.FILES_ONLY && filesSelected && !folderSelected) {
        acceptEnabled = true;
      } else if (selectionMode == SelectionMode.DIRS_ONLY && !filesSelected && folderSelected) {
        acceptEnabled = true;
      } else if (selectionMode == SelectionMode.DIRS_AND_FILES) {
        acceptEnabled = true;
      }
    } else {
      FileObject selectedFileObject = getSelectedFileObject();
      FileType type = selectedFileObject.getType();
      if (selectionMode == SelectionMode.FILES_ONLY && type == FileType.FILE ||
          selectionMode == SelectionMode.DIRS_ONLY && type == FileType.FOLDER) {
        acceptEnabled = true;
      } else if (SelectionMode.DIRS_AND_FILES == selectionMode) {
        acceptEnabled = true;
      }
    }

    if (actionApproveDelegate != null) {
      actionApproveDelegate.setEnabled(acceptEnabled);
    }
    actionApproveButton.setEnabled(acceptEnabled);
  }

  public FileObject getCurrentLocation() {
    return currentLocation;
  }

  public MutableListModel getFavoritesUserListModel() {
    return favoritesUserListModel;
  }

  public void showLoading() {
    LOGGER.trace("Showing loading panel");
    loadingProgressBar.setIndeterminate(true);
    loadingProgressBar.setString(Messages.getMessage("browser.loading..."));
    skipCheckingLinksButton.setSelected(false);
    cardLayout.show(tablePanel, LOADING);
  }

  public void showTable() {
    LOGGER.trace("Showing result table");
    tableScrollPane.getVerticalScrollBar().setValue(0);
    cardLayout.show(tablePanel, TABLE);
  }

  public boolean isMultiSelectionEnabled() {
    return multiSelectionEnabled;
  }

  public void setMultiSelectionEnabled(boolean b) {
    int selectionMode = b ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION;
    tableFiles.getSelectionModel().setSelectionMode(selectionMode);
    if (multiSelectionEnabled == b) {
      return;
    }
    boolean oldValue = multiSelectionEnabled;
    multiSelectionEnabled = b;
    firePropertyChange(MULTI_SELECTION_ENABLED_CHANGED_PROPERTY, oldValue, multiSelectionEnabled);
    try {
      selectionChanged();
    } catch (FileSystemException e) {
      LOGGER.error("Error during update state", e);
    }
  }

  public SelectionMode getSelectionMode() {
    return selectionMode;
  }

  public void setSelectionMode(SelectionMode mode) {
    if (selectionMode == mode) {
      return;
    }
    SelectionMode oldValue = selectionMode;
    this.selectionMode = mode;
    firePropertyChange(MULTI_SELECTION_MODE_CHANGED_PROPERTY, oldValue, selectionMode);
    try {
      selectionChanged();
    } catch (FileSystemException e) {
      LOGGER.error("Error during update state", e);
    }
  }

  public void setApproveAction(Action action) {
    actionApproveDelegate = action;
    actionApproveButton.setAction(actionApproveDelegate);
    if (action != null) {
      actionApproveButton.setText(String.format("%s [Ctrl+Enter]", actionApproveDelegate.getValue(Action.NAME)));
    }
    try {
      selectionChanged();
    } catch (FileSystemException e) {
      LOGGER.warn("Problem with checking selection conditions", e);
    }
  }

  public void setCancelAction(Action cancelAction) {
    actionCancelDelegate = cancelAction;
    actionCancelButton.setAction(actionCancelDelegate);
    try {
      selectionChanged();
    } catch (FileSystemException e) {
      LOGGER.warn("Problem with checking selection conditions", e);
    }

  }

  public FileObject getSelectedFileObject() {
    int selectedRow = tableFiles.getSelectedRow();
    if (selectedRow > -1) {
      return vfsTableModel.get(tableFiles.convertRowIndexToModel(selectedRow));
    }
    return null;
  }

  public FileObject[] getSelectedFiles() {
    int[] selectedRows = tableFiles.getSelectedRows();
    FileObject[] fileObjects = new FileObject[selectedRows.length];
    for (int i = 0; i < selectedRows.length; i++) {
      fileObjects[i] = vfsTableModel.get(tableFiles.convertRowIndexToModel(selectedRows[i]));
    }
    return fileObjects;
  }

  private final class QuickSearchKeyAdapter extends KeyAdapter {

    private long lastTimeTyped = 0;
    private long typeTimeout = 500;
    private static final String LETTERS = "qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    private static final String DIGITS = "0123456789";
    private static final String OTHER_CHARS = "!@#$%^&*()()-_=+[];:'\",./ ";
    private static final String ALLOWED_CHARS = LETTERS + DIGITS + OTHER_CHARS;
    private StringBuilder sb;

    public QuickSearchKeyAdapter() {
      sb = new StringBuilder();
    }

    @Override
    public void keyTyped(KeyEvent e) {
      char keyChar = e.getKeyChar();
      if (ALLOWED_CHARS.indexOf(keyChar) > -1) {
        if (System.currentTimeMillis() > lastTimeTyped + typeTimeout) {
          sb.setLength(0);
        }
        sb.append(keyChar);
        selectNextFileStarting(sb.toString());
        lastTimeTyped = System.currentTimeMillis();
      }

    }
  }

  private final class BaseNavigateActionGoUp extends BaseNavigateAction {
    private BaseNavigateActionGoUp(VfsBrowser browser) {
      super(browser);
      putValue(SMALL_ICON, Icons.getInstance().getArrowTurn90());
      putValue(SHORT_DESCRIPTION, Messages.getMessage("nav.goFolderUp"));
    }


    @Override
    public void performLongOperation(CheckBeforeActionResult actionResult) {
      LOGGER.info("Executing going up");
      try {
        goToUrl(currentLocation.getParent());
      } catch (FileSystemException e) {
        LOGGER.error("Error go UP", e);
      }
    }


    @Override
    protected boolean canGoUrl() {
      try {
        FileObject parent = currentLocation.getParent();
        return parent != null && VFSUtils.canGoUrl(parent);
      } catch (FileSystemException e) {
        LOGGER.error("Can't get parent of current location", e);
      }
      return false;
    }


    @Override
    protected boolean canExecuteDefaultAction() {
      return false;
    }
  }

  private final class BaseNavigateActionOpen extends BaseNavigateAction {
    private BaseNavigateActionOpen(VfsBrowser browser) {
      super(browser);
    }


    @Override
    public void performLongOperation(CheckBeforeActionResult checkBeforeActionResult) {
      int selectedRow = tableFiles.getSelectedRow();
      FileObject fileObject = vfsTableModel.get(tableFiles.convertRowIndexToModel(selectedRow));
      if (canExecuteDefaultAction() && actionApproveButton.isEnabled()) {
        actionApproveButton.doClick();
      } else {
        goToUrl(fileObject);
      }
    }


    @Override
    protected boolean canGoUrl() {
      int selectedRow = tableFiles.getSelectedRow();
      if (selectedRow > -1) {
        FileObject fileObject = vfsTableModel.get(tableFiles.convertRowIndexToModel(selectedRow));
        return VFSUtils.canGoUrl(fileObject);
      }
      return false;

    }

    @Override
    protected boolean canExecuteDefaultAction() {
      int selectedRow = tableFiles.getSelectedRow();
      if (SelectionMode.FILES_ONLY.equals(selectionMode) || SelectionMode.DIRS_AND_FILES.equals(selectionMode)) {
        if (selectedRow > -1) {
          FileObject fileObject = vfsTableModel.get(tableFiles.convertRowIndexToModel(selectedRow));
          try {
            return FileType.FILE.equals(fileObject.getType()) || FileType.FILE_OR_FOLDER.equals(fileObject.getType());
          } catch (FileSystemException e) {
            LOGGER.warn("Cant' get file type", e);
          }
        }
      }
      return false;
    }
  }

  private final class BaseNavigateActionRefresh extends BaseNavigateAction {
    private BaseNavigateActionRefresh(VfsBrowser browser) {
      super(browser);
      putValue(SMALL_ICON, Icons.getInstance().getArrowCircleDouble());
      putValue(SHORT_DESCRIPTION, Messages.getMessage("nav.refreshActionLabelText"));
    }

    @Override
    public void performLongOperation(CheckBeforeActionResult checkBeforeActionResult) {
      try {
        currentLocation.refresh();
      } catch (FileSystemException e) {
        LOGGER.error("Can't refresh location", e);
      }
      goToUrl(currentLocation);
    }

    @Override
    protected boolean canGoUrl() {
      return true;
    }

    @Override
    protected boolean canExecuteDefaultAction() {
      return false;
    }
  }

  public void selectNextFileStarting(String string) {
    LOGGER.debug("Looking for file starting with {}", string);
    int selectedRow = tableFiles.getSelectedRow();
    selectedRow = selectedRow < 0 ? 0 : selectedRow;
    LOGGER.debug("Starting search with row {}", selectedRow);
    boolean fullLoop;
    int started = selectedRow;
    do {
      LOGGER.debug("Checking table row {}", selectedRow);
      int convertRowIndexToModel = tableFiles.convertRowIndexToModel(selectedRow);
      LOGGER.debug("Table row {} is row {} from model", selectedRow, convertRowIndexToModel);
      FileObject fileObject = vfsTableModel.get(convertRowIndexToModel);
      LOGGER.debug("Checking {} if begins with {}", fileObject.getName().getBaseName(), string);
      if (fileObject.getName().getBaseName().toLowerCase().startsWith(string.toLowerCase())) {
        tableFiles.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        tableFiles.scrollRectToVisible(new Rectangle(tableFiles.getCellRect(selectedRow, 0, true)));
        break;
      }
      selectedRow++;
      selectedRow = selectedRow >= tableFiles.getRowCount() ? 0 : selectedRow;
      fullLoop = selectedRow == started;
    } while (!fullLoop);
  }


}
