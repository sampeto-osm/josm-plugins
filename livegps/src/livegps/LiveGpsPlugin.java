// License: Public Domain. For details, see LICENSE file.
package livegps;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Shortcut;

public class LiveGpsPlugin extends Plugin implements LayerChangeListener {
    private boolean enabled = false;
    private LiveGpsAcquirer acquirer = null;
    private Thread acquirerThread = null;
    private LiveGpsAcquirerNMEA acquirerNMEA = null;
    private Thread acquirerNMEAThread = null;
    private JMenu lgpsmenu = null;
    private JCheckBoxMenuItem lgpscapture;
    private JCheckBoxMenuItem lgpsautocenter;
    private LiveGpsDialog lgpsdialog;
    /* List of foreign (e.g. other plugins) subscribers */
    List<PropertyChangeListener> listenerQueue = new ArrayList<>();

    private GpxData data = new GpxData();
    private LiveGpsLayer lgpslayer = null;

    public class CaptureAction extends JosmAction {
        public CaptureAction() {
            super(
                    tr("Capture GPS Track"),
                    "capturemenu",
                    tr("Connect to gpsd server and show current position in LiveGPS layer."),
                    Shortcut.registerShortcut("menu:livegps:capture", tr(
                            "GPS: {0}", tr("Capture GPS Track")),
                            KeyEvent.VK_R, Shortcut.CTRL), true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            enableTracking(lgpscapture.isSelected());
        }
    }

    public class CenterAction extends JosmAction {
        public CenterAction() {
            super(tr("Center Once"), "centermenu",
                    tr("Center the LiveGPS layer to current position."),
                    Shortcut.registerShortcut("edit:centergps", tr("GPS: {0}",
                            tr("Center Once")), KeyEvent.VK_HOME,
                            Shortcut.DIRECT), true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (lgpslayer != null) {
                lgpslayer.center();
            }
        }
    }

    public class AutoCenterAction extends JosmAction {
        public AutoCenterAction() {
            super(
                    tr("Auto-Center"),
                    "autocentermenu",
                    tr("Continuously center the LiveGPS layer to current position."),
                    Shortcut.registerShortcut("menu:livegps:autocenter", tr(
                            "GPS: {0}", tr("Auto-Center")),
                            KeyEvent.VK_HOME, Shortcut.CTRL), true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (lgpslayer != null) {
                setAutoCenter(lgpsautocenter.isSelected());
            }
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        Layer oldLayer = e.getRemovedLayer();
        if (oldLayer != lgpslayer)
            return;

        enableTracking(false);
        lgpscapture.setSelected(false);
        MainApplication.getLayerManager().removeLayerChangeListener(this);
        lgpslayer = null;
    }

    public LiveGpsPlugin(PluginInformation info) {
        super(info);
        MainMenu menu = MainApplication.getMenu();
        lgpsmenu = menu.gpsMenu;
        if (lgpsmenu.getItemCount() > 0) {
            lgpsmenu.addSeparator();
        }

        JosmAction captureAction = new CaptureAction();
        lgpscapture = new JCheckBoxMenuItem(captureAction);
        lgpsmenu.add(lgpscapture);
        lgpscapture.setAccelerator(captureAction.getShortcut().getKeyStroke());

        JosmAction centerAction = new CenterAction();
        MainMenu.add(lgpsmenu, centerAction);

        JosmAction autoCenterAction = new AutoCenterAction();
        lgpsautocenter = new JCheckBoxMenuItem(autoCenterAction);
        lgpsmenu.add(lgpsautocenter);
        lgpsautocenter.setAccelerator(autoCenterAction.getShortcut().getKeyStroke());
    }

    /**
     * Set to <code>true</code> if the current position should always be in the center of the map.
     * @param autoCenter if <code>true</code> the map is always centered.
     */
    public void setAutoCenter(boolean autoCenter) {
        lgpsautocenter.setSelected(autoCenter); // just in case this method was
        // not called from the menu
        if (lgpslayer != null) {
            lgpslayer.setAutoCenter(autoCenter);
            if (autoCenter)
                lgpslayer.center();
        }
    }

    /**
     * Returns <code>true</code> if autocenter is selected.
     * @return <code>true</code> if autocenter is selected.
     */
    public boolean isAutoCenter() {
        return lgpsautocenter.isSelected();
    }

    /**
     * Enable or disable gps tracking
     * @param enable if <code>true</code> tracking is started.
     */
    public void enableTracking(boolean enable) {

        if (enable && !enabled) {
            if (lgpslayer == null) {
                lgpslayer = new LiveGpsLayer(data);
                MainApplication.getLayerManager().addLayer(lgpslayer);
                MainApplication.getLayerManager().addLayerChangeListener(this);
                lgpslayer.setAutoCenter(isAutoCenter());
            }

            assert (acquirer == null);
            assert (acquirerThread == null);

            if (!Config.getPref().getBoolean(LiveGpsAcquirer.C_DISABLED)) {

                acquirer = new LiveGpsAcquirer();
                acquirerThread = new Thread(acquirer);

                acquirer.addPropertyChangeListener(lgpslayer);
                acquirer.addPropertyChangeListener(lgpsdialog);
                for (PropertyChangeListener listener : listenerQueue) {
                    acquirer.addPropertyChangeListener(listener);
                }

                acquirerThread.start();
            }

            assert (acquirerNMEA == null);
            assert (acquirerNMEAThread == null);

            if (!Config.getPref().get(LiveGpsAcquirerNMEA.C_SERIAL).isEmpty()) {
                acquirerNMEA = new LiveGpsAcquirerNMEA();
                acquirerNMEAThread = new Thread(acquirerNMEA);
                acquirerNMEA.addPropertyChangeListener(lgpslayer);
                acquirerNMEA.addPropertyChangeListener(lgpsdialog);

                for (PropertyChangeListener listener : listenerQueue) {
                    acquirerNMEA.addPropertyChangeListener(listener);
                }

                acquirerNMEAThread.start();
            }

            enabled = true;
        } else if (!enable && enabled) {
            assert (lgpslayer != null);
            assert (acquirer != null);
            assert (acquirerThread != null);

            if (acquirerThread != null) {
                acquirer.shutdown();
                acquirer = null;
                acquirerThread = null;
            }

            if (acquirerNMEAThread != null) {
                acquirerNMEA.shutdown();
                acquirerNMEA = null;
                acquirerNMEAThread = null;
            }

            enabled = false;
        }
    }

    /**
     * Add a listener for gps events.
     * @param listener the listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        assert !listenerQueue.contains(listener);

        listenerQueue.add(listener);
        if (acquirer != null)
            acquirer.addPropertyChangeListener(listener);
        if (acquirerNMEA != null)
            acquirerNMEA.addPropertyChangeListener(listener);
    }

    /**
     * Remove a listener for gps events.
     * @param listener the listener.
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        assert listenerQueue.contains(listener);

        listenerQueue.remove(listener);
        if (acquirer != null)
            acquirer.removePropertyChangeListener(listener);
        if (acquirerNMEA != null)
            acquirerNMEA.removePropertyChangeListener(listener);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null)
            newFrame.addToggleDialog(lgpsdialog = new LiveGpsDialog(newFrame));
    }

    /**
     * Return the LiveGPS menu
     * @return the {@code JMenu} entry
     */
    public JMenu getLgpsMenu() {
        return this.lgpsmenu;
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new LiveGPSPreferences();
    }
}
