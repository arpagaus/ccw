package clojuredev.outline;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import clojure.lang.Keyword;
import clojuredev.ClojuredevPlugin;
import clojuredev.debug.IClojureClientProvider;

public class ClojureNSOutlinePage extends Page implements
        IContentOutlinePage, ISelectionChangedListener {
    private ListenerList selectionChangedListeners = new ListenerList();

    private TreeViewer treeViewer;
    
	private final IClojureClientProvider clojureClientProvider;
	
	private static final Keyword KEYWORD_NAME = Keyword.intern(null, "name");
	private static final Keyword KEYWORD_CHILDREN = Keyword.intern(null, "children");
	private static final Keyword KEYWORD_TYPE = Keyword.intern(null, "type");
    private static final Keyword KEYWORD_PRIVATE = Keyword.intern(null, "private");
    private static final Keyword KEYWORD_DOC = Keyword.intern(null, "doc");
    private static final Keyword KEYWORD_ARGLISTS = Keyword.intern(null, "arglists");
    
    private Composite control;
	private Text filterText;
	private String patternString = "";
	private Pattern pattern;
	private boolean searchInName = true;
	private boolean searchInDoc = false;
	private ISelection selectionBeforePatternSearchBegan;
	private Object[] expandedElementsBeforeSearchBegan;

	
	public ClojureNSOutlinePage(IClojureClientProvider clojureClientProvider) {
		this.clojureClientProvider = clojureClientProvider;
	}
	
    @Override
	public void createControl(Composite theParent) {
		control = new Composite(theParent, SWT.NONE);
		
		GridLayout gl = new GridLayout();
		gl.numColumns = 4;
		control.setLayout(gl);
		
		Label l = new Label(control, SWT.NONE);
		l.setText("Find :");
		l.setToolTipText("Enter an expression on which the browser will filter, based on name and doc string of symbols");
		GridData gd = new GridData();
		gd.verticalAlignment = SWT.CENTER;
		l.setLayoutData(gd);
		
		filterText = new Text(control, SWT.FILL | SWT.BORDER);
		filterText.setTextLimit(10);
		filterText.setToolTipText("Enter here a word to search. It can be a regexp. e.g. \"-map$\" (without double quotes) for matching strings ending with -map");
		gd = new GridData();
		gd.horizontalAlignment = SWT.FILL;
		gd.verticalAlignment = SWT.CENTER;
		filterText.setLayoutData(gd);
		filterText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				patternString = ((Text) e.getSource()).getText();
				if ("".equals(patternString.trim())) {
					if (pattern != null) {
						// user stops search, we restore the previous state of the tree
						pattern = null;
						treeViewer.refresh(false);
						treeViewer.setExpandedElements(expandedElementsBeforeSearchBegan);
						treeViewer.setSelection(selectionBeforePatternSearchBegan);
						selectionBeforePatternSearchBegan = null;
						expandedElementsBeforeSearchBegan = null;
					}
				} else {
					pattern = Pattern.compile(patternString.trim());
					if (selectionBeforePatternSearchBegan==null) {
						// user triggers search, we save the current state of the tree
						selectionBeforePatternSearchBegan = treeViewer.getSelection();
						expandedElementsBeforeSearchBegan = treeViewer.getExpandedElements();
					}
					treeViewer.refresh(false);
					treeViewer.expandAll();
				}
			}
		});
		
		Button inName = new Button(control, SWT.CHECK);
		gd = new GridData();
		gd.verticalAlignment = SWT.CENTER;
		inName.setLayoutData(gd);
		inName.setText("in name");
		inName.setToolTipText("Press to enable the search in the name");
		inName.setSelection(true);
		inName.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				searchInName = ((Button) e.getSource()).getSelection();
				treeViewer.refresh(false);
			}
		});
		
		Button inDoc = new Button(control, SWT.CHECK);
		gd = new GridData();
		gd.verticalAlignment = SWT.CENTER;
		inDoc.setLayoutData(gd);
		inDoc.setText("in doc");
		inDoc.setToolTipText("Press to enable the search in the documentation string");
		inDoc.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				searchInDoc = ((Button) e.getSource()).getSelection();
				treeViewer.refresh(false);
			}
		});
		
        treeViewer = new TreeViewer(control, SWT.MULTI | SWT.H_SCROLL
                | SWT.V_SCROLL);
        treeViewer.addSelectionChangedListener(this);
		gd = new GridData();//SWT.FILL, SWT.FILL, true, true, 2, 1);
		gd.horizontalSpan = 4;
		gd.horizontalAlignment = SWT.FILL;
		gd.grabExcessHorizontalSpace = true;
		gd.verticalAlignment = SWT.FILL;
		gd.grabExcessVerticalSpace = true;
		treeViewer.getControl().setLayoutData(gd);
		
        ColumnViewerToolTipSupport.enableFor(treeViewer);
        
        treeViewer.setContentProvider(new ContentProvider());
        treeViewer.setLabelProvider(new LabelProvider());
		
        treeViewer.setSorter(new NSSorter());
        treeViewer.setComparer(new IElementComparer() {
			public boolean equals(Object a, Object b) {
				if (a == b) {
					return true;
				}
				if ( (a==null && b!=null) || (b==null && a!=null) ) {
					return false;
				}

				if (a instanceof Map && b instanceof Map
				        && ((Map) a).get(KEYWORD_NAME)!=null && ((Map) b).get(KEYWORD_NAME)!=null) {
				    return ((Map) a).get(KEYWORD_NAME).equals(((Map) b).get(KEYWORD_NAME));
				} else {
					return a.equals(b);
				}
			}

			public int hashCode(Object element) {
				if (element == null) {
					return 0;
				}
				if ( element instanceof Map && ((Map) element).get(KEYWORD_NAME)!=null) {
					return ((Map) element).get(KEYWORD_NAME).hashCode();
				} else {
					return element.hashCode();
				}
			}
		});
        
        treeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (patternString==null || patternString.trim().equals("")) {
					return true;
				}
				Map parent = (Map) parentElement;
				Map elem = (Map) element;
				if ("var".equals(elem.get(KEYWORD_TYPE))) {
					String name = (String) elem.get(KEYWORD_NAME);
					boolean nameMatches = searchInName && name!=null && pattern.matcher(name).find();
					
					String doc = (String) elem.get(KEYWORD_DOC);
					boolean docMatches = searchInDoc && doc!=null && pattern.matcher(doc).find();
					
					return nameMatches || docMatches;
				} else {
					return true;
				}
			}});
	}
	
	private static class ContentProvider implements ITreeContentProvider {
		private Object input; 
		public Object[] getElements(Object inputElement) {
			return getChildren(inputElement);
		}

		public void dispose() {
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			this.input = newInput;
		}

		public Object[] getChildren(Object parentElement) {
			if (Map.class.isInstance(parentElement)) {
			    Collection children = (Collection) ((Map) parentElement).get(KEYWORD_CHILDREN);
			    if (children == null) {
			        return new Object[0];
			    } else {
			        return children.toArray();
			    }
			} else {
				return new Object[0];
			}
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object parentElement) {
			if (Map.class.isInstance(parentElement)) {
			    return ((Map) parentElement).get(KEYWORD_CHILDREN) != null;
			} else {
				return false;
			}
		}
		
	}
	
	private static class LabelProvider extends CellLabelProvider {
	    
        public String getToolTipText(Object element) {
            StringBuilder result = new StringBuilder();
            
            
            Object maybeArglist = ((Map) element).get(KEYWORD_ARGLISTS);
            if (maybeArglist != null) {
                result.append("arglists: ");
                result.append(maybeArglist);
            }
            
            Object maybeDoc = ((Map) element).get(KEYWORD_DOC);
            if (maybeDoc != null) {
                if (result.length() > 0) {
                    result.append("\n\n");
                }
                result.append(maybeDoc);
            }
            
            if (result.length() != 0) {
                return result.toString();
            } else {
                return "no documentation information";
            }
        }
	    
        public Point getToolTipShift(Object object) {
            return new Point(5,15);
        }

        public int getToolTipDisplayDelayTime(Object object) {
            return 100;
        }

        public int getToolTipTimeDisplayed(Object object) {
            return 15000;
        }
        
        public void update(ViewerCell cell) {
            cell.setText(getText(cell.getElement()));
            cell.setImage(getImage(cell.getElement()));
            
        }
	    
		private String getText(Object element) {
			if (Map.class.isInstance(element)) {
			    return (String) ((Map) element).get(KEYWORD_NAME);
			} else {
				return element.toString();
			}
		}
		
		private Image getImage(Object element) {
			if (Map.class.isInstance(element)) {
			    Map node = (Map) element;
			    if ("ns".equals(node.get(KEYWORD_TYPE))) {
	                return ClojuredevPlugin.getDefault().getImageRegistry().get(ClojuredevPlugin.NS);
			    } else {
			        if ("true".equals(node.get(KEYWORD_PRIVATE))) {
			            return ClojuredevPlugin.getDefault().getImageRegistry().get(ClojuredevPlugin.PRIVATE_FUNCTION);
			        } else {
                        return ClojuredevPlugin.getDefault().getImageRegistry().get(ClojuredevPlugin.PUBLIC_FUNCTION);
			        }
			    }
			}
			return null;
		}
	}
	
	private static class NSSorter extends ViewerSorter {
		
	}
	
	private Map<String, List<String>> getRemoteNsTree() {
		Object result = clojureClientProvider.getClojureClient().invokeStr("(clojuredev.debug.serverrepl/namespaces-info)");
		System.out.println("invokeStr called");
		return (Map<String, List<String>>) result;
	}

    public void refresh() {
    	if (treeViewer == null) {
    		return;
    	}
    	
		Object oldInput = treeViewer.getInput();
    	final Object newInput = getRemoteNsTree();
		if (oldInput!=null && oldInput.equals(newInput)) {
			return;
		}
    	
        if (Display.getCurrent() == null) {
            final Display display = PlatformUI.getWorkbench().getDisplay();
            display.asyncExec(new Runnable() {
                public void run() {
                	refreshTreeViewer(newInput);
                }
            });
        } else {
        	refreshTreeViewer(newInput);
        }
    }
    
    private void refreshTreeViewer(Object newInput) {
    	ISelection sel = treeViewer.getSelection();
    	TreePath[] expandedTreePaths = treeViewer.getExpandedTreePaths();

    	treeViewer.setInput(newInput);

    	treeViewer.setExpandedTreePaths(expandedTreePaths);
    	treeViewer.setSelection(sel);
    }
    
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        selectionChangedListeners.add(listener);
    }
    
    /**
     * Fires a selection changed event.
     *
     * @param selection the new selection
     */
    protected void fireSelectionChanged(ISelection selection) {
        // create an event
        final SelectionChangedEvent event = new SelectionChangedEvent(this,
                selection);

        // fire the event
        Object[] listeners = selectionChangedListeners.getListeners();
        for (int i = 0; i < listeners.length; ++i) {
            final ISelectionChangedListener l = (ISelectionChangedListener) listeners[i];
            SafeRunner.run(new SafeRunnable() {
                public void run() {
                    l.selectionChanged(event);
                }
            });
        }
    }

    public Control getControl() {
        if (control == null) {
			return null;
		}
        return control;
    }

    public ISelection getSelection() {
        if (treeViewer == null) {
			return StructuredSelection.EMPTY;
		}
        return treeViewer.getSelection();
    }

    public void init(IPageSite pageSite) {
        super.init(pageSite);
        pageSite.setSelectionProvider(this);
    }

    public void removeSelectionChangedListener(
            ISelectionChangedListener listener) {
        selectionChangedListeners.remove(listener);
    }

    public void selectionChanged(SelectionChangedEvent event) {
        fireSelectionChanged(event.getSelection());
    }

    /**
     * Sets focus to a part in the page.
     */
    public void setFocus() {
        treeViewer.getControl().setFocus();
    }

    public void setSelection(ISelection selection) {
        if (treeViewer != null) {
			treeViewer.setSelection(selection);
		}
    }
}