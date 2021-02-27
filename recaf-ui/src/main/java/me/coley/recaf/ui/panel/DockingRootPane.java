package me.coley.recaf.ui.panel;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import com.panemu.tiwulfx.control.dock.DetachableTabPaneFactory;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import me.coley.recaf.RecafUI;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Docking pane manager that handles creation of new {@link DetachableTabPane} instances.
 * Provides wrapper utility for adding new tabs to the most recently updated {@link DetachableTabPane}.
 *
 * @author Matt Coley
 */
public class DockingRootPane extends BorderPane {
	private final DetachableTabPaneFactory tabPaneFactory = new DockingTabPaneFactory();
	private final Map<String, Tab> titleToTab = new HashMap<>();
	private SplitPane root = new SplitPane();
	private DetachableTabPane recentTabPane;

	/**
	 * Create new pane.
	 */
	public DockingRootPane() {
		setCenter(root);
	}

	/**
	 * Create a new split, the next added tab will be created in the new split.
	 *
	 * @param orientation
	 * 		Orientation of the split.
	 * @param dividerPositions
	 * 		Divider positions of items added to the new split.
	 *
	 * @return New root {@link SplitPane}.
	 */
	public SplitPane createNewSplit(Orientation orientation, double... dividerPositions) {
		// TODO: The initial space of the initial SplitPane never gets closed (drag workspace nav into adjacent tabpane)
		//   - But if we re-create the layout with dragging tabs into place the space is freeable...
		//   - Whats wrong with this setup?
		// Remove root
		setCenter(null);
		SplitPane oldRoot = root;
		// Create new split
		root = new SplitPane();
		root.setOrientation(orientation);
		root.setDividerPositions(dividerPositions);
		root.getItems().add(oldRoot);
		setCenter(root);
		// Invalidate recent tab pane.
		recentTabPane = null;
		return root;
	}

	/**
	 * @return Most recently interacted with tab pane.
	 */
	public DetachableTabPane getRecentTabPane() {
		return recentTabPane;
	}

	/**
	 * @param recentTabPane
	 * 		New tab pane to mark as recent.
	 */
	public void setRecentTabPane(DetachableTabPane recentTabPane) {
		this.recentTabPane = recentTabPane;
	}

	private void add(Tab tab) {
		if (recentTabPane == null) {
			DetachableTabPane tabPane = new DetachableTabPane();
			tabPane.setDetachableTabPaneFactory(tabPaneFactory);
			tabPane.getTabs().add(tab);
			root.getItems().add(tabPane);
			recentTabPane = tabPane;
		} else {
			recentTabPane.getTabs().add(tab);
		}
	}

	/**
	 * Select an existing tab with the given title, or create a new one if it does not exist.
	 *
	 * @param title
	 * 		Tab title.
	 * @param contentFallback
	 * 		Tab content provider if the tab needs to be created.
	 */
	public void openTab(String title, Supplier<Node> contentFallback) {
		// Select if existing tab with title exists
		Tab target = titleToTab.get(title);
		if (target != null) {
			TabPane parent = target.getTabPane();
			parent.getSelectionModel().select(target);
		}
		// Create new tab if it does not exist
		createTab(title, contentFallback.get());
	}

	/**
	 * Creates a new tab.
	 *
	 * @param title
	 * 		Tab title.
	 * @param content
	 * 		Tab content.
	 */
	public void createTab(String title, Node content) {
		Tab tab = new Tab(title, content);
		add(tab);
	}

	/**
	 * Creates a new tab that cannot be closed.
	 *
	 * @param title
	 * 		Tab title.
	 * @param content
	 * 		Tab content.
	 */
	public void createLockedTab(String title, Node content) {
		Tab tab = new Tab(title, content);
		tab.setClosable(false);
		add(tab);
	}

	/**
	 * A {@link DetachableTabPane} factory that ensures newly made {@link DetachableTabPane} instances update the
	 * {@link #titleToTab title to tab lookup} and {@link #getRecentTabPane()}  most recently interacted} with tab pane.
	 */
	private class DockingTabPaneFactory extends DetachableTabPaneFactory {
		@Override
		protected void init(DetachableTabPane newTabPane) {
			newTabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
				while (c.next()) {
					updateTabLookup(c);
					updateRecentTabPane(newTabPane, c);
					updateStageClosable(newTabPane);
				}
			});
		}

		/**
		 * Update the associated stage
		 * @param tabPane Tab pane updated.
		 */
		private void updateStageClosable(DetachableTabPane tabPane) {
			boolean closable = true;
			for (Tab tab : tabPane.getTabs()) {
				closable &= tab.isClosable();
			}
			// For newly spawned windows (stages) do not let them be closable if they contain a tab that is marked as
			// not closable. The closable tabs of the window can still be closed though.
			Stage stage = (Stage) tabPane.getScene().getWindow();
			if (!closable && !stage.equals(RecafUI.getWindows().getMainWindow())) {
				stage.setOnCloseRequest(e -> {
					tabPane.getTabs().removeIf(Tab::isClosable);
					e.consume();
				});
			}
		}

		/**
		 * Update the {@link #getRecentTabPane() recent tab pane} if the change includes added items.
		 * Closing a tab does not count as an interaction since the intention is that if a user adds a tab
		 * to the pane, future tabs should be added to it as well.
		 *
		 * @param tabPane
		 * 		Tab pane interacted with.
		 * @param c
		 * 		Change event.
		 */
		private void updateRecentTabPane(DetachableTabPane tabPane, ListChangeListener.Change<? extends Tab> c) {
			if (c.wasAdded() && c.getAddedSize() > 0) {
				recentTabPane = tabPane;
			}
		}

		/**
		 * Update the lookup of {@link #titleToTab tab titles to tab instances}.
		 *
		 * @param c
		 * 		Change event.
		 */
		private void updateTabLookup(ListChangeListener.Change<? extends Tab> c) {
			if (c.wasAdded()) {
				for (Tab newTab : c.getAddedSubList()) {
					titleToTab.put(newTab.getText(), newTab);
				}
			}
			if (c.wasRemoved()) {
				for (Tab removedTab : c.getRemoved()) {
					titleToTab.remove(removedTab.getText());
				}
			}
		}
	}
}
