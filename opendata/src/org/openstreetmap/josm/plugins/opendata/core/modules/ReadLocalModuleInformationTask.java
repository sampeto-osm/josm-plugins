// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.opendata.core.modules;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.opendata.OdPlugin;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.xml.sax.SAXException;

/**
 * This is an asynchronous task for reading module information from the files
 * in the local module repositories.
 *
 * It scans the files in the local modules repository (see {@link OdPlugin#getModulesDirectory()}
 * and extracts module information from three kind of files:
 * <ul>
 *   <li>.jar-files, assuming that they represent module jars</li>
 *   <li>.jar.new-files, assuming that these are downloaded but not yet installed modules</li>
 *   <li>cached lists of available modules, downloaded for instance from
 *   <a href="https://svn.openstreetmap.org/applications/editors/josm/plugins/opendata/modules.txt">OSM SVN</a></li>
 * </ul>
 */
public class ReadLocalModuleInformationTask extends PleaseWaitRunnable {
    private Map<String, ModuleInformation> availableModules;
    private boolean canceled;

    public ReadLocalModuleInformationTask() {
        super(tr("Reading local module information.."), false);
        availableModules = new HashMap<>();
    }

    public ReadLocalModuleInformationTask(ProgressMonitor monitor) {
        super(tr("Reading local module information.."), monitor, false);
        availableModules = new HashMap<>();
    }

    @Override
    protected void cancel() {
        canceled = true;
    }

    @Override
    protected void finish() {}

    protected void processJarFile(File f, String moduleName) throws ModuleException {
        ModuleInformation info = new ModuleInformation(
                f,
                moduleName
                );
        if (!availableModules.containsKey(info.getName())) {
            info.localversion = info.version;
            availableModules.put(info.getName(), info);
        } else {
            ModuleInformation current = availableModules.get(info.getName());
            current.localversion = info.version;
            if (info.icon != null) {
                current.icon = info.icon;
            }
            current.className = info.className;
            current.libraries = info.libraries;
        }
    }

    protected void scanSiteCacheFiles(ProgressMonitor monitor, File modulesDirectory) {
        File[] siteCacheFiles = modulesDirectory.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.matches("^([0-9]+-)?site.*\\.txt$");
                    }
                }
                );
        if (siteCacheFiles == null || siteCacheFiles.length == 0)
            return;
        monitor.subTask(tr("Processing module site cache files..."));
        monitor.setTicksCount(siteCacheFiles.length);
        for (File f: siteCacheFiles) {
            String fname = f.getName();
            monitor.setCustomText(tr("Processing file ''{0}''", fname));
            try {
                processLocalModuleInformationFile(f);
            } catch (ModuleListParseException e) {
                Logging.warn(tr("Warning: Failed to scan file ''{0}'' for module information. Skipping.", fname));
                e.printStackTrace();
            }
            monitor.worked(1);
        }
    }

    protected void scanIconCacheFiles(ProgressMonitor monitor, File modulesDirectory) {
        File[] siteCacheFiles = modulesDirectory.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.matches("^([0-9]+-)?site.*modules-icons\\.zip$");
                    }
                }
                );
        if (siteCacheFiles == null || siteCacheFiles.length == 0)
            return;
        monitor.subTask(tr("Processing module site cache icon files..."));
        monitor.setTicksCount(siteCacheFiles.length);
        for (File f: siteCacheFiles) {
            String fname = f.getName();
            monitor.setCustomText(tr("Processing file ''{0}''", fname));
            for (ModuleInformation pi : availableModules.values()) {
                if (pi.icon == null && pi.iconPath != null) {
                    pi.icon = new ImageProvider(pi.name+".jar/"+pi.iconPath)
                            .setArchive(f)
                            .setMaxWidth(24)
                            .setMaxHeight(24)
                            .setOptional(true).get();
                }
            }
            monitor.worked(1);
        }
    }

    protected void scanModuleFiles(ProgressMonitor monitor, File modulesDirectory) {
        File[] moduleFiles = modulesDirectory.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".jar") || name.endsWith(".jar.new");
                    }
                }
                );
        if (moduleFiles == null || moduleFiles.length == 0)
            return;
        monitor.subTask(tr("Processing module files..."));
        monitor.setTicksCount(moduleFiles.length);
        for (File f: moduleFiles) {
            String fname = f.getName();
            monitor.setCustomText(tr("Processing file ''{0}''", fname));
            try {
                if (fname.endsWith(".jar")) {
                    String moduleName = fname.substring(0, fname.length() - 4);
                    processJarFile(f, moduleName);
                } else if (fname.endsWith(".jar.new")) {
                    String moduleName = fname.substring(0, fname.length() - 8);
                    processJarFile(f, moduleName);
                }
            } catch (ModuleException e) {
                Logging.warn(tr("Warning: Failed to scan file ''{0}'' for module information. Skipping.", fname));
                e.printStackTrace();
            }
            monitor.worked(1);
        }
    }

    protected void scanLocalModuleRepository(ProgressMonitor monitor, File modulesDirectory) {
        if (modulesDirectory == null) return;
        try {
            monitor.beginTask("");
            scanSiteCacheFiles(monitor, modulesDirectory);
            scanIconCacheFiles(monitor, modulesDirectory);
            scanModuleFiles(monitor, modulesDirectory);
        } finally {
            monitor.setCustomText("");
            monitor.finishTask();
        }
    }

    protected void processLocalModuleInformationFile(File file) throws ModuleListParseException {
        try (FileInputStream fin = new FileInputStream(file)) {
            List<ModuleInformation> pis = new ModuleListParser().parse(fin);
            for (ModuleInformation pi : pis) {
                // we always keep module information from a module site because it
                // includes information not available in the module jars Manifest, i.e.
                // the download link or localized descriptions
                //
                availableModules.put(pi.name, pi);
            }
        } catch (IOException e) {
            throw new ModuleListParseException(e);
        }
    }

    protected void analyseInProcessModules() {
        for (Module module : ModuleHandler.moduleList) {
            ModuleInformation info = module.getModuleInformation();
            if (canceled) return;
            if (!availableModules.containsKey(info.name)) {
                availableModules.put(info.name, info);
            } else {
                availableModules.get(info.name).localversion = info.localversion;
            }
        }
    }

    @Override
    protected void realRun() throws SAXException, IOException, OsmTransferException {
        Collection<String> moduleLocations = ModuleInformation.getModuleLocations();
        getProgressMonitor().setTicksCount(moduleLocations.size() + 2);
        if (canceled) return;
        for (String location : moduleLocations) {
            scanLocalModuleRepository(
                    getProgressMonitor().createSubTaskMonitor(1, false),
                    new File(location)
                    );
            getProgressMonitor().worked(1);
            if (canceled) return;
        }
        analyseInProcessModules();
        getProgressMonitor().worked(1);
        if (canceled) return;
        getProgressMonitor().worked(1);
    }

    /**
     * Replies information about available modules detected by this task.
     *
     * @return information about available modules detected by this task.
     */
    public List<ModuleInformation> getAvailableModules() {
        return new ArrayList<>(availableModules.values());
    }

    /**
     * Replies true if the task was canceled by the user
     *
     * @return true if the task was canceled by the user
     */
    public boolean isCanceled() {
        return canceled;
    }
}
