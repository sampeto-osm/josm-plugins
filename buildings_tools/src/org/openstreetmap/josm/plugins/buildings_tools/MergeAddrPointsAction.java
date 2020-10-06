// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.buildings_tools;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;

public class MergeAddrPointsAction extends JosmAction {

    public MergeAddrPointsAction() {
        super(tr("Merge address points"), "mergeaddr",
                tr("Move tags from address nodes inside buildings to building ways"),
                Shortcut.registerShortcut("edit:mergeaddrpoints", tr("Edit: {0}", tr("Merge address points")),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        if (!isEnabled())
            return;
        Collection<OsmPrimitive> selection = getLayerManager().getEditDataSet().getSelected();
        if (selection.isEmpty()) {
            new Notification(tr("Select both address nodes and building ways to merge"))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE).show();
            return;
        }
        List<Node> addrNodes = new LinkedList<>();
        List<Way> buildings = new LinkedList<>();
        for (OsmPrimitive p : selection) {
            if (p.getType() == OsmPrimitiveType.NODE) {
                boolean refsOK = true;
                for (OsmPrimitive r : p.getReferrers()) {
                    if (r.getType() == OsmPrimitiveType.WAY) {
                        // Don't use nodes if they're referenced by ways
                        refsOK = false;
                        break;
                    }
                }
                if (!refsOK)
                    continue;
                for (String key : p.getKeys().keySet()) {
                    if (key.startsWith("addr:")) {
                        addrNodes.add((Node) p); // Found address node
                        break;
                    }
                }
            } else if (p.getType() == OsmPrimitiveType.WAY && p.getKeys().containsKey("building"))
                buildings.add((Way) p);
        }
        if (addrNodes.isEmpty()) {
            new Notification(tr("No address nodes found in the selection"))
                    .setIcon(JOptionPane.ERROR_MESSAGE).show();
            return;
        }
        if (buildings.isEmpty()) {
            new Notification(tr("No building ways found in the selection"))
                    .setIcon(JOptionPane.ERROR_MESSAGE).show();
            return;
        }

        // find nodes covered by more than one building, see #17625
        Map<Node, Way> nodeToWayMap = new HashMap<>();
        Set<Way> overlappingWays = new HashSet<>();
        for (Way w : buildings) {
            for (Node n : addrNodes) {
                if (Geometry.nodeInsidePolygon(n, w.getNodes())) {
                    Way old = nodeToWayMap.put(n, w);
                    if (old != null) {
                        overlappingWays.add(w);
                        overlappingWays.add(old);
                    }
                }
            }
        }
        buildings.removeAll(overlappingWays);

        List<Command> cmds = new LinkedList<>();
        int multi = 0;
        int conflicts = 0;
        List<Pair<Node, Way>> replaced = new ArrayList<>();
        Set<Relation> modifiedRelations = new HashSet<>();
        for (Way w : buildings) {
            Node mergeNode = null;
            int oldMulti = multi;
            for (Node n : addrNodes) {
                if (Geometry.nodeInsidePolygon(n, w.getNodes())) {
                    if (mergeNode != null) {
                        multi++;
                        // Multiple address nodes inside one building --
                        // skipping
                        break;
                    } else {
                        mergeNode = n;
                    }
                }
            }
            if (oldMulti != multi)
                continue;
            if (mergeNode != null) {
                boolean hasConflicts = false;
                Map<String, String> tags = new HashMap<>();
                for (Entry<String, String> entry : mergeNode.getKeys().entrySet()) {
                    String newValue = entry.getValue();
                    if (newValue == null)
                        continue;
                    String oldValue = w.getKeys().get(entry.getKey());
                    if (!newValue.equals(oldValue)) {
                        if (oldValue == null) {
                            tags.put(entry.getKey(), newValue);
                        } else
                            hasConflicts = true;
                    }
                }
                if (hasConflicts)
                    conflicts++;
                if (!tags.isEmpty())
                    cmds.add(new ChangePropertyCommand(Collections.singleton(w), tags));
                if (!hasConflicts) {
                    replaced.add(Pair.create(mergeNode, w));
                    modifiedRelations.addAll(mergeNode.referrers(Relation.class).collect(Collectors.toList()));
                }
            }
        }

        for (Relation r : modifiedRelations) {
            Relation rnew = new Relation(r);
            boolean modified = false;
            for (Pair<Node, Way> repl : replaced) {
                for (int i = 0; i < rnew.getMembersCount(); i++) {
                    RelationMember member = rnew.getMember(i);
                    if (repl.a.equals(member.getMember())) {
                        rnew.removeMember(i);
                        rnew.addMember(i, new RelationMember(member.getRole(), repl.b));
                        modified = true;
                    }
                }
            }
            if (modified) {
                cmds.add(new ChangeCommand(r, rnew));
            }
        }
        if (!replaced.isEmpty()) {
            cmds.add(new DeleteCommand(replaced.stream().map(p -> p.a).collect(Collectors.toList())));
        }

        if (multi != 0)
            new Notification(trn("There is {0} building with multiple address nodes inside",
                    "There are {0} buildings with multiple address nodes inside", multi, multi))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show();
        if (conflicts != 0)
            new Notification(trn("There is {0} building with address conflicts",
                            "There are {0} buildings with address conflicts", conflicts, conflicts))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show();
        if (!overlappingWays.isEmpty())
            new Notification(tr("There are {0} buildings covering the same address node", overlappingWays.size()))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show();
        if (cmds.isEmpty() && multi == 0 && conflicts == 0 && overlappingWays.isEmpty())
            new Notification(tr("No address nodes inside buildings found"))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE).show();
        if (!cmds.isEmpty())
            UndoRedoHandler.getInstance().add(new SequenceCommand("Merge addresses", cmds));
    }

    @Override
    protected boolean listenToSelectionChange() {
        return false;
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }
}
