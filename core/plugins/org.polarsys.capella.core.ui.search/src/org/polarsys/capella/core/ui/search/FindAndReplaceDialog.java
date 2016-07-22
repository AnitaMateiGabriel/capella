/*******************************************************************************
 * Copyright (c) 2006, 2016 THALES GLOBAL SERVICES.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *  
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.core.ui.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.sirius.viewpoint.DRepresentation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.misc.StringMatcher;
import org.polarsys.capella.common.data.modellingcore.AbstractNamedElement;
import org.polarsys.capella.common.ef.command.AbstractCompoundCommand;
import org.polarsys.capella.common.ef.command.AbstractReadWriteCommand;
import org.polarsys.capella.common.ef.command.ICommand;
import org.polarsys.capella.common.helpers.EcoreUtil2;
import org.polarsys.capella.common.helpers.TransactionHelper;
import org.polarsys.capella.common.re.ReNamedElement;
import org.polarsys.capella.common.tools.report.appenders.reportlogview.LightMarkerRegistry;
import org.polarsys.capella.common.tools.report.appenders.reportlogview.MarkerView;
import org.polarsys.capella.common.tools.report.appenders.reportlogview.MarkerViewPlugin;
import org.polarsys.capella.common.ui.toolkit.dialogs.SelectElementsDialog;
import org.polarsys.capella.common.ui.toolkit.viewers.AbstractContextMenuFiller;
import org.polarsys.capella.core.data.capellacore.CapellaElement;
import org.polarsys.capella.core.data.capellamodeller.SystemEngineering;
import org.polarsys.capella.core.model.handler.provider.CapellaAdapterFactoryProvider;
import org.polarsys.capella.core.model.utils.saxparser.WriteCapellaElementDescriptionSAXParser;
import org.polarsys.capella.core.platform.sirius.ui.navigator.CapellaNavigatorPlugin;
import org.polarsys.capella.core.platform.sirius.ui.navigator.IImageKeys;
import org.polarsys.capella.core.platform.sirius.ui.navigator.actions.LocateInCapellaExplorerAction;
import org.polarsys.capella.core.ui.semantic.browser.view.SemanticBrowserView;
import org.polarsys.capella.core.ui.toolkit.dialogs.ImpactAnalysisDialog;
import org.polarsys.kitalpha.emde.model.Element;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 */
public class FindAndReplaceDialog extends SelectElementsDialog {

  private static final String SPACE = " "; //$NON-NLS-1$

  private Pattern _pattern;

  private static final int REPLACE_ALL_BUTTON = IDialogConstants.CLIENT_ID + 1;
  private static final int COMPUTE_IMPACT_BUTTON = IDialogConstants.CLIENT_ID + 2;
  private FindAndReplaceHeader _header;
  private ISelection _currentSelection;

  private Predicate<Element> _isNameMatching;

  private Predicate<Element> _isSummaryMatching;

  private Predicate<Element> _isDescriptionMatching;

  boolean _ignoreWildCards;
  private boolean _wholeExpression;
  private boolean _wholeModelSearch;
  private boolean _nameSearch;
  private boolean _summarySearch;
  private boolean _descriptionSearch;
  private SystemEngineering _systemEngineering;

  private Set<Element> _scope;
  // TODO use a matchingMap:HashMap
  private Set<Element> _matchingElementsForName;

  private Set<Element> _matchingElementsForSummary;

  private Set<Element> _matchingElementsForDescription;

  private boolean _updateHyperlinks;

  private boolean _ignoreCase;

  private Predicate<CapellaElement> _isDescriptionMatchingByWords;

  /**
   * Create the dialog.
   * @param parentShell
   * @param elements
   * @param treeViewerExpandLevel
   * @wbp.parser.constructor
   */
  protected FindAndReplaceDialog(Shell parentShell, Collection<? extends EObject> elements, int treeViewerExpandLevel) {
    super(parentShell, TransactionHelper.getEditingDomain(elements), CapellaAdapterFactoryProvider.getInstance().getAdapterFactory(),
          org.polarsys.capella.core.ui.search.Messages.FindAndReplaceDialog_title,
          org.polarsys.capella.core.ui.search.Messages.FindAndReplaceDialog_dialogMessage, elements, true, null, treeViewerExpandLevel);

  }

  /**
   * @param shell
   * @param root
   * @param modelElementContent
   * @param selection
   * @param treeViewerExpandLevel
   */
  public FindAndReplaceDialog(Shell shell, SystemEngineering root, HashSet<EObject> modelElementContent, ISelection selection,
      int treeViewerExpandLevel) {
    this(shell, modelElementContent, treeViewerExpandLevel);
    _currentSelection = selection;
    _systemEngineering = root;
  }

  /**
   * Overridden to add accelerators.
   * {@inheritDoc}
   */
  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    // Create buttons.
    Button reportImpactButton = createButton(parent, COMPUTE_IMPACT_BUTTON, Messages.FindAndReplaceDialog_compute_impact, false);
    Button replaceAllButton = createButton(parent, REPLACE_ALL_BUTTON, Messages.FindAndReplaceDialog_replace_all, false);
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, false);

    reportImpactButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        handlePreviewImpact();
      }

    });
    replaceAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        handleReplaceAll(e.getSource());
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doCreateDialogArea(Composite parent) {
    // add the header
    _header = new FindAndReplaceHeader(parent, SWT.NO_BACKGROUND);
    super.doCreateDialogArea(parent);
    getViewer().getClientViewer().setSelection(_currentSelection, true);
    getViewer().getClientViewer().getTree().showSelection();

    _isNameMatching = new Predicate<Element>() {
      @Override
      public boolean apply(Element elt) {
        if (elt instanceof AbstractNamedElement) {
          return match(((AbstractNamedElement) elt).getName());
        } else if (elt instanceof ReNamedElement) {
          return match(((ReNamedElement) elt).getName());

        }
        return false;
      }
    };

    _isSummaryMatching = new Predicate<Element>() {
      @Override
      public boolean apply(Element elt) {
        return elt instanceof CapellaElement ? match(((CapellaElement) elt).getSummary()) : false;
      }
    };
    _isDescriptionMatching = new Predicate<Element>() {
      @Override
      public boolean apply(Element elt) {
        return elt instanceof CapellaElement ? match(((CapellaElement) elt).getDescription()) : false;
      }
    };

    // other version that matches description words
    _isDescriptionMatchingByWords = new Predicate<CapellaElement>() {
      @Override
      public boolean apply(CapellaElement elt) {
        String description = elt.getDescription();
        if (null == description) {
          return false;
        }
        List<String> words = Arrays.asList(description.split(SPACE));

        for (String word : words) {
          if (match(word)) {
            return true;
          }
        }
        return false;
      }
    };

    _matchingElementsForSummary = Sets.newHashSet();
    _matchingElementsForName = Sets.newHashSet();
    _matchingElementsForDescription = Sets.newHashSet();

    _header.getWholeModelRadioButton().addSelectionListener(new SelectionAdapter() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void widgetSelected(SelectionEvent e) {
        getViewer().setEnabled(false);
      }
    });
    _header.getSelectedElementsRadioBtn().addSelectionListener(new SelectionAdapter() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void widgetSelected(SelectionEvent e) {
        getViewer().setEnabled(true);
      }
    });
  }

  /**
   * 
   */
  private void getUserChoices() {
    _ignoreWildCards = !_header.isWildCardsChecked();
    _ignoreCase = !_header.isCaseSensitiveChecked();
    _wholeModelSearch = _header.isWholeModelChecked();
    _wholeExpression = _header.isWholeExpression();
    _nameSearch = _header.isNameChecked();
    _summarySearch = _header.isSummaryChecked();
    _descriptionSearch = _header.isDescriptionChecked();
    _updateHyperlinks = _header.isUpdateHyperlinks();
  }

  void handlePreviewImpact() {
    getUserChoices();
    updateScope();
    updatePattern();
    updateMatchingElements();

    _header.getImpactedElementsLabel().setText(_matchingElementsForName.size() + SPACE);
    _header.getNbImpactedSummaries().setText(_matchingElementsForSummary.size() + SPACE);
    _header.getNbImpactedDescs().setText(_matchingElementsForDescription.size() + SPACE);

    SetView<Element> impactedElts = Sets.union(Sets.union(_matchingElementsForName, _matchingElementsForSummary), _matchingElementsForDescription);

    // no matching elements
    if (impactedElts.size() == 0) {
      MessageDialog.openInformation(getParentShell(), Messages.FindAndReplaceDialog_title, Messages.FindAndReplaceDialog_no_matching);
    }
    // Some elements matches => opens a preview dialog
    if (impactedElts.size() > 0) {
      ImpactAnalysisDialog impactDialog =
          new ImpactAnalysisDialog(new ArrayList<EObject>(impactedElts), Messages.FindAndReplaceDialog_preview, NLS.bind(
              Messages.FindAndReplaceDialog_impacted_find_string, getFindString()));
      AbstractContextMenuFiller contextMenuFiller = new PreviewDialogContextMenuFiller();
      impactDialog.setContextMenuManagerFiller(contextMenuFiller);
      impactDialog.open();
    }
  }

  void handleReplaceAll(Object source) {

    getUserChoices();
    updateScope();
    updatePattern();
    updateMatchingElements();

    ICommand replaceAllInNameCmd =
        CommandFactory.createReplaceAllInNameCommand(_matchingElementsForName, getFindString(), getReplaceString(), false, _ignoreWildCards, false, false);
    ICommand replaceAllInSummaryCmd =
        CommandFactory.createReplaceAllInSummary(_matchingElementsForSummary, getFindString(), getReplaceString(), false, _ignoreWildCards, false, false);
    ICommand replaceAllInDescCmd =
        CommandFactory.createReplaceAllInDescription(_matchingElementsForDescription, getFindString(), getReplaceString(), false, _ignoreWildCards, false,
            false);

    // execute the compoundcommand to replace names, summaries and descriptions
    AbstractCompoundCommand replaceAllCommand = new CompoundCommand();
    replaceAllCommand.append(replaceAllInNameCmd);
    replaceAllCommand.append(replaceAllInSummaryCmd);
    replaceAllCommand.append(replaceAllInDescCmd);

    if (replaceAllCommand.getContainedCommandsSize() > 0) {
      TransactionHelper.getExecutionManager(_systemEngineering).execute(replaceAllCommand);
    }

    // update description hyperlinks to impacted elements
    if (_updateHyperlinks) {
      updateHyperlinksInDescriptions();
    }

    // report results to user
    reportResults(Messages.FindAndReplaceDialog_impacted_names, _matchingElementsForName);
    reportResults(Messages.FindAndReplaceDialog_impacted_summaries, _matchingElementsForSummary);
    reportResults(Messages.FindAndReplaceDialog_impacted_descriptions, _matchingElementsForDescription);

  }

  /**
   * 
   */
  @SuppressWarnings("unchecked")
  private void updateHyperlinksInDescriptions() {
    // warn: not optimized implementation, scans the whole system engineering content
    // TODO reduce the scope update to referencing descriptions
    final Set<EObject> SysEngAllContents = (Set<EObject>) EcoreUtil2.getAllContents(Collections.singleton(_systemEngineering));
    final WriteCapellaElementDescriptionSAXParser writeDescription = new WriteCapellaElementDescriptionSAXParser() {
      /**
       * {@inheritDoc}
       */
      @Override
      protected String getName(EObject object) {
        String result = super.getName(object);
        if ((null == result) || result.isEmpty()) {
          if (object instanceof DRepresentation) {
            DRepresentation res = (DRepresentation) object;
            String repName = res.getName();
            if (null != repName) {
              result = repName;
            }
          }
        }
        return result;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      protected boolean managedObject(EObject object) {
        return super.managedObject(object) || (object instanceof DRepresentation);
      }
    };
    ICommand updateHyperlinksCommand = new AbstractReadWriteCommand() {

      @Override
      public void run() {
        writeDescription.updateDescription(new ArrayList<EObject>(SysEngAllContents));

      }

    };
    TransactionHelper.getExecutionManager(SysEngAllContents).execute(updateHyperlinksCommand);
  }

  /**
   * 
   */
  private void updateMatchingElements() {
    if (_nameSearch) {
      _matchingElementsForName = ImmutableSet.copyOf(Sets.filter(_scope, _isNameMatching));
    } else {
      _matchingElementsForName = Collections.emptySet();
    }
    if (_summarySearch) {
      _matchingElementsForSummary = ImmutableSet.copyOf(Sets.filter(_scope, _isSummaryMatching));
    } else {
      _matchingElementsForSummary = Collections.emptySet();
    }
    if (_descriptionSearch) {
      _matchingElementsForDescription = ImmutableSet.copyOf(Sets.filter(_scope, _isDescriptionMatching));
    } else {
      _matchingElementsForDescription = Collections.emptySet();
    }
  }

  /**
   * 
   */
  private void updatePattern() {
    if (!_ignoreWildCards) {
      return;
    }
    if (_ignoreWildCards) {
      _pattern = Pattern.compile(getFindString(), (_ignoreCase ? Pattern.CASE_INSENSITIVE : 0) | Pattern.LITERAL);
    } else {
      _pattern = Pattern.compile(getFindString(), _ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    }
  }

  /**
   * @return
   */
  @SuppressWarnings("unchecked")
  private void updateScope() {
    if (_wholeModelSearch) {
      _scope = (Set<Element>) EcoreUtil2.getAllContents(Collections.singleton(_systemEngineering));
    } else { // scope is the selected elements in the tree viewer
      List<? extends EObject> selected = handleResult();
      // append contents of selected elements to the list
      // TODO transform
      _scope = (Set<Element>) EcoreUtil2.getAllContents(selected);
    }

  }

  /**
   * @return
   */
  public String getFindString() {
    String findText = _header.getFindCombo().getText();
    return findText;
  }

  /**
   * @return
   */
  public String getReplaceString() {
    return _header.getReplaceCombo().getText();
  }

  /**
   * Answers whether the given String matches the pattern.
   * @param string the String to test
   * @return whether the string matches the pattern
   */
  boolean match(String string) {
    if (null == string) {
      return false;
    }
    if (!_ignoreWildCards) {// if wildcards are used

      return matchWithWildCard(string);
    }
    if (_wholeExpression) {
      List<String> findExprList = Arrays.asList(getFindString().split(SPACE));
      List<String> string_pList = Arrays.asList(string.split(SPACE));

      return matchExpressionList(string_pList, findExprList, _ignoreCase);
    }
    return _pattern.matcher(string).find();
  }

  /**
   * @param string
   * @return
   */
  @SuppressWarnings("restriction")
  private boolean matchWithWildCard(String string) {
    StringMatcher wildCardMatcher = new StringMatcher(getFindString(), _ignoreCase, _ignoreWildCards);
    return wildCardMatcher.match(string);
  }

  public static boolean matchExpressionList(List<String> text, List<String> findExpr, boolean ignoreCase) {
    if ((null == text) | (null == findExpr)) {
      return false;
    }
    int textSize = text.size();
    int findExprSize = findExpr.size();
    if (findExprSize > textSize) {
      return false;
    }
    for (int i = 0; i < textSize; i++) {
      if (areEquals(findExpr.get(0), text.get(i), ignoreCase)) {
        List<String> textSubList = text.subList(i + 1, i + findExprSize);
        List<String> fExprSublist = findExpr.subList(1, findExprSize);
        if (areEquals(fExprSublist, textSubList, ignoreCase)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @param l1
   * @param l2
   * @param ignoreCase
   * @return
   */
  private static boolean areEquals(List<String> l1, List<String> l2, boolean ignoreCase) {
    if (l2.size() != l1.size()) {
      return false;
    }
    if (ignoreCase) {
      for (int i = 0; i < l2.size(); i++) {
        if (!l1.get(i).equalsIgnoreCase(l2.get(i))) {
          return false;
        }
      }
    } else {
      return l1.equals(l2);
    }
    return true;
  }

  /**
   * @param string
   * @param string2
   * @param ignoreCase
   * @return
   */
  private static boolean areEquals(String string, String string2, boolean ignoreCase) {
    if (ignoreCase) {
      return string2.equalsIgnoreCase(string);
    }
    return string2.equals(string);
  }

  /**
   * reports impacted elements count to the InfoView
   * @param message
   * @param impactedElements
   */
  private void reportResults(String message, final Set<Element> impactedElements) {
    final int impactedCount = impactedElements.size();
    final String informationMessage =  NLS.bind(message, Integer.valueOf(impactedCount));
    LightMarkerRegistry.getInstance().createMarker(ResourcesPlugin.getWorkspace().getRoot(), new BasicDiagnostic(Messages.FindAndReplaceDialog_1, 0, informationMessage, impactedElements.toArray()));
    try {
      // Show the Information view
      PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(MarkerView.VIEW_ID, null, IWorkbenchPage.VIEW_VISIBLE);
    } catch (PartInitException exception) {
      MarkerViewPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, MarkerViewPlugin.PLUGIN_ID, exception.getLocalizedMessage(), exception));
    }
  }

  /**
   */
  protected final class PreviewDialogContextMenuFiller extends AbstractContextMenuFiller {
    @Override
    public void fillMenuManager(IMenuManager contextMenuManager, final ISelection selection) {
      final LocateInCapellaExplorerAction selectInExplorerAction = new LocateInCapellaExplorerAction() {
        /**
         * {@inheritDoc}
         */
        @Override
        protected ISelection getSelection() {
          return selection;
        }
      };

      IAction action = new Action() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
          selectInExplorerAction.run(this);
        }

      };

      // Ignore workbench part site, since in a dialog, site has no meaning.
      selectInExplorerAction.shouldIgnoreWorkbenchPartSite(true);
      action.setText(Messages.ImpactAnalysisAction_ShowInCapellaExplorer_Title);
      action.setImageDescriptor(CapellaNavigatorPlugin.getDefault().getImageDescriptor(IImageKeys.IMG_SHOW_IN_CAPELLA_EXPLORER));
      selectInExplorerAction.selectionChanged(action, selection);
      if (action.isEnabled()) {
        contextMenuManager.add(action);
      }

      final EObject eObject = (EObject) ((TreeSelection) selection).iterator().next();

      final LocateInCapellaExplorerAction selectInSemanticBrowserAction = new LocateInCapellaExplorerAction() {
        /**
         * {@inheritDoc}
         */
        @Override
        protected ISelection getSelection() {
          return selection;
        }
      };

      IAction action3 = new Action() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
          try {
            activateSemanticBrowser();
          } catch (CoreException e) {
            // Do nothing
          }
          selectInSemanticBrowserAction.run(this);
        }

        private void activateSemanticBrowser() throws CoreException {
          IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
          SemanticBrowserView smView = (SemanticBrowserView) activePage.findView(SemanticBrowserView.SEMANTIC_BROWSER_ID);
          if (null == smView) {
            // Show it if not found.
            smView = (SemanticBrowserView) activePage.showView(SemanticBrowserView.SEMANTIC_BROWSER_ID);
          }
          activePage.activate(smView);
          smView.setInput(eObject);
        }

      };

      // Ignore workbench part site, since in a dialog, site has no meaning.
      selectInSemanticBrowserAction.shouldIgnoreWorkbenchPartSite(true);
      action3.setText(Messages.selectInSemanticBrowser);
      action3.setImageDescriptor(CapellaNavigatorPlugin.getDefault().getImageDescriptor(IImageKeys.IMG_SHOW_IN_CAPELLA_EXPLORER));
      selectInSemanticBrowserAction.selectionChanged(action3, selection);
      if (action3.isEnabled()) {
        contextMenuManager.add(action3);
      }

    }
  }

  class CompoundCommand extends AbstractCompoundCommand {
    /**
     * {@inheritDoc}
     */
    @Override
    public void append(ICommand command) {
      if (null != command) {
        super.append(command);
      }
    }

  }

}
