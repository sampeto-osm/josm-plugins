// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.opendata.core.io.geographic;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.spi.JsonProvider;
import javax.swing.JOptionPane;

import org.geotools.geometry.jts.JTS;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.AbstractIdentifiedObject;
import org.geotools.referencing.CRS;
import org.geotools.referencing.NamedIdentifier;
import org.geotools.referencing.crs.AbstractCRS;
import org.geotools.referencing.crs.AbstractDerivedCRS;
import org.geotools.referencing.crs.AbstractSingleCRS;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.datum.Datum;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.OperationNotFoundException;
import org.opengis.referencing.operation.TransformException;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.tests.DuplicateWay;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.AbstractReader;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.opendata.core.OdConstants;
import org.openstreetmap.josm.plugins.opendata.core.gui.DialogPrompter;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageOverlay;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.ReflectionUtils;
import org.openstreetmap.josm.tools.UserCancelException;
import org.openstreetmap.josm.tools.Utils;

/**
 * Superclass of geographic format readers (currently GML, GPKG, and SHP).
 */
public abstract class GeographicReader extends AbstractReader {

    protected static CoordinateReferenceSystem wgs84;
    static {
        try {
            wgs84 = CRS.decode("EPSG:4326");
        } catch (FactoryException e) {
            Logging.error(e);
        }
    }

    private final GeographicHandler handler;
    private final GeographicHandler[] defaultHandlers;

    protected final Map<LatLon, Node> nodes;

    protected CoordinateReferenceSystem crs;
    protected MathTransform transform;

    private static final Map<String, Integer> esriWkid = new TreeMap<>();

    protected GeographicReader(GeographicHandler handler, GeographicHandler[] defaultHandlers) {
        this.nodes = new HashMap<>();
        this.handler = handler;
        this.defaultHandlers = defaultHandlers;
    }

    @Override
    protected DataSet doParseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        return null;
    }

    public GeographicHandler getHandler() {
        return this.handler;
    }

    protected Node getNode(Point p, LatLon key) {
        Node n = nodes.get(key);
        if (n == null && handler != null && handler.checkNodeProximity()) {
            LatLon ll = new LatLon(p.getY(), p.getX());
            for (Node node : nodes.values()) {
                if (node.equalsEpsilon(ll, ILatLon.MAX_SERVER_PRECISION)) {
                    return node;
                }
            }
        }
        return n;
    }

    protected Node createOrGetNode(Point p) throws MismatchedDimensionException, TransformException {
        return createOrGetNode(p, null);
    }

    protected Node createOrGetNode(Point p, String ele) throws MismatchedDimensionException, TransformException {
        if (!p.isValid()) {
            throw new IllegalArgumentException("Invalid point: " + p);
        }
        Point p2 = (Point) JTS.transform(p, transform);
        LatLon key = new LatLon(p2.getY(), p2.getX());
        Node n = getNode(p2, key);
        if (n == null) {
            n = new Node(key);
            if (ele != null) {
                n.put("ele", ele);
            }
            if (handler == null || handler.useNodeMap()) {
                nodes.put(key, n);
            }
            ds.addPrimitive(n);
        } else if (n.getDataSet() == null) {
            // handler may have removed the node from DataSet (see Paris public light handler for example)
            ds.addPrimitive(n);
        }
        return n;
    }

    protected Node createOrGetEmptyNode(Point p) throws MismatchedDimensionException, TransformException {
        Point p2 = (Point) JTS.transform(p, transform);
        LatLon key = new LatLon(p2.getY(), p2.getX());
        Node n = getNode(p2, key);
        if (n != null && n.hasKeys()) {
            n = null;
        }
        if (n == null) {
            n = new Node(key);
            if (handler == null || handler.useNodeMap()) {
                nodes.put(key, n);
            }
            ds.addPrimitive(n);
        } else if (n.getDataSet() == null) {
            // handler may have removed the node from DataSet (see Paris public light handler for example)
            ds.addPrimitive(n);
        }
        return n;
    }

    protected <T extends OsmPrimitive> T addOsmPrimitive(T p) {
        ds.addPrimitive(p);
        return p;
    }

    protected final Way createWay() {
        return addOsmPrimitive(new Way());
    }

    protected final Way createOrGetWay(LineString ls) {
        Way w = null;
        Way tempWay = new Way();
        if (ls != null) {
            final List<Node> nodes = new ArrayList<>(ls.getNumPoints());
            // Build list of nodes
            for (int i = 0; i < ls.getNumPoints(); i++) {
                try {
                    nodes.add(createOrGetNode(ls.getPointN(i)));
                } catch (TransformException | IllegalArgumentException e) {
                    Logging.error("Exception for " + ls + ": " + e.getClass().getName() + ": " + e.getMessage());
                }
            }
            tempWay.setNodes(nodes);
            // Find possible duplicated ways
            if (tempWay.getNodesCount() > 0) {
                Collection<Way> candidates = Utils.filteredCollection(tempWay.firstNode().getReferrers(), Way.class);
                candidates.remove(tempWay);
                List<LatLon> tempNodes = DuplicateWay.getOrderedNodes(tempWay);
                for (Way candidate : candidates) {
                    List<LatLon> candNodesA = DuplicateWay.getOrderedNodes(candidate);
                    List<LatLon> candNodesB = new ArrayList<>(candNodesA);
                    Collections.reverse(candNodesB);
                    if (tempNodes.equals(candNodesA) || tempNodes.equals(candNodesB)) {
                        w = candidate;
                        break;
                    }
                }
            }
        }
        // If no duplicate way found, create new one
        if (w == null) {
            w = createWay();
            w.setNodes(tempWay.getNodes());
        }
        return w;
    }

    protected final Relation createMultipolygon() {
        Relation r = new Relation();
        r.put("type", "multipolygon");
        return addOsmPrimitive(r);
    }

    protected static void addWayToMp(Relation r, String role, Way w) {
        r.addMember(new RelationMember(role, w));
    }

    /**
     * returns true if the user wants to cancel, false if they
     * want to continue
     */
    protected static boolean warnLenientMethod(final Component parent, final CoordinateReferenceSystem crs) {
        return new DialogPrompter<ExtendedDialog>() {
            @Override
            protected ExtendedDialog buildDialog() {
                final ExtendedDialog dlg = new ExtendedDialog(parent,
                        tr("Cannot transform to WGS84"),
                        tr("Cancel"), tr("Continue"));
                // CHECKSTYLE.OFF: LineLength
                dlg.setContent("<html>" +
                        tr("JOSM was unable to find a strict mathematical transformation between ''{0}'' and WGS84.<br /><br />"+
                        "Do you want to try a <i>lenient</i> method, which will perform a non-precise transformation (<b>with location errors up to 1 km</b>) ?<br/><br/>"+
                        "If so, <b>do NOT upload</b> such data to OSM !", crs.getName())+
                        "</html>");
                // CHECKSTYLE.ON: LineLength
                dlg.setButtonIcons(new ImageProvider("cancel").setMaxSize(ImageSizes.LARGEICON).get(),
                        new ImageProvider("ok").setMaxSize(ImageSizes.LARGEICON).addOverlay(
                                new ImageOverlay(new ImageProvider("warning-small"), 0.5, 0.5, 1.0, 1.0)).get());
                dlg.setToolTipTexts(tr("Cancel"),
                        tr("Try lenient method"));
                dlg.setIcon(JOptionPane.WARNING_MESSAGE);
                dlg.setCancelButton(1);
                return dlg;
            }
        }.promptInEdt().getValue() != 2;
    }

    private static void compareDebug(CoordinateReferenceSystem crs1, CoordinateReferenceSystem crs2) {
        Logging.debug("-- COMPARING "+crs1.getName()+" WITH "+crs2.getName()+" --");
        compareDebug("class", crs1.getClass(), crs2.getClass());
        CoordinateSystem cs1 = crs1.getCoordinateSystem();
        CoordinateSystem cs2 = crs2.getCoordinateSystem();
        if (!compareDebug("cs", cs1, cs2)) {
            int dim1 = cs1.getDimension();
            int dim2 = cs2.getDimension();
            if (compareDebug("cs.dim", dim1, dim2)) {
                for (int i = 0; i < dim1; i++) {
                    compareDebug("cs.axis"+i, cs1.getAxis(i), cs1.getAxis(i));
                }
            }
        }
        if (crs1 instanceof AbstractSingleCRS) {
            Datum datum1 = ((AbstractSingleCRS) crs1).getDatum();
            Datum datum2 = ((AbstractSingleCRS) crs2).getDatum();
            if (!compareDebug("datum", datum1, datum2) && datum1 instanceof AbstractIdentifiedObject) {
                AbstractIdentifiedObject adatum1 = (AbstractIdentifiedObject) datum1;
                AbstractIdentifiedObject adatum2 = (AbstractIdentifiedObject) datum2;
                compareDebug("datum.name1", adatum1.nameMatches(adatum2.getName().getCode()), adatum1.getName(), adatum2.getName());
                compareDebug("datum.name2", adatum2.nameMatches(adatum1.getName().getCode()), adatum2.getName(), adatum1.getName());
            }
            if (crs1 instanceof AbstractDerivedCRS) {
                AbstractDerivedCRS adcrs1 = (AbstractDerivedCRS) crs1;
                AbstractDerivedCRS adcrs2 = (AbstractDerivedCRS) crs2;
                compareDebug("baseCRS", adcrs1.getBaseCRS(), adcrs2.getBaseCRS());
                compareDebug("conversionFromBase", adcrs1.getConversionFromBase(), adcrs2.getConversionFromBase());
            }
        }
        Logging.debug("-- COMPARING FINISHED --");
    }

    private static boolean compareDebug(String text, Object o1, Object o2) {
        return compareDebug(text, o1.equals(o2), o1, o2);
    }

    private static boolean compareDebug(String text, IdentifiedObject o1, IdentifiedObject o2) {
        return compareDebug(text, (AbstractIdentifiedObject) o1, (AbstractIdentifiedObject) o2);
    }

    private static boolean compareDebug(String text, AbstractIdentifiedObject o1, AbstractIdentifiedObject o2) {
        return compareDebug(text, o1.equals(o2, false), o1, o2);
    }

    private static boolean compareDebug(String text, boolean result, Object o1, Object o2) {
        Logging.debug(text + ": " + result + "("+o1+", "+o2+")");
        return result;
    }

    private static InputStream getEsriWkidStream() {
        InputStream in = GeographicReader.class.getResourceAsStream(OdConstants.ESRI_WKID);
        if (in == null) {
            // Setup different for unit tests
            in = GeographicReader.class.getResourceAsStream(OdConstants.ESRI_WKID.replaceFirst("/resources", ""));
        }
        return in;
    }

    private static void loadEsriWkid() throws IOException {
        Logging.info("Loading ESRI WKID database...");
        try (InputStream in = getEsriWkidStream();
            JsonReader json = JsonProvider.provider().createReader(in)) {
            JsonObject root = json.readObject();
            JsonArray wkids = root.getJsonArray("wkids");
            JsonArray labels = root.getJsonArray("labels");
            if (wkids.size() != labels.size()) {
                throw new IllegalStateException();
            }
            for (int i = 0; i < wkids.size(); i++) {
                esriWkid.put(labels.getString(i), wkids.getInt(i));
            }
        }
        Logging.info("ESRI WKID database loaded");
    }

    private static Integer getEpsgCodeFromEsriWkid(String wkid) {
        if (esriWkid.isEmpty()) {
            try {
                loadEsriWkid();
            } catch (IOException e) {
                Logging.error(e);
            }
        }
        return esriWkid.get(wkid);
    }

    /**
     * Find the math transform for the CRS used by this reader
     * @param parent The parent component, used for showing dialogs
     * @param findSimiliarCrs {@code true} if we don't need to find the exact CRS
     * @throws FactoryException See {@link CRS#findMathTransform}, {@link org.opengis.referencing.AuthorityFactory#getAuthorityCodes}
     * @throws UserCancelException If the user cancelled in one of the message dialogs
     * @throws GeoMathTransformException If no transform could be found
     */
    protected void findMathTransform(Component parent, boolean findSimiliarCrs)
            throws FactoryException, UserCancelException, GeoMathTransformException {
        try {
            // Geotools relies on Authority identifiers to find suitable transformations
            // but this information is optional in WKT and not defined in ESRI WKID database, so often missing in real-world files
            if (crs instanceof AbstractIdentifiedObject && crs.getIdentifiers().isEmpty()) {
                // If no identifier, attempt to find one in embedded ESRI database
                String wkid = crs.getName().getCode();
                if (wkid != null) {
                    Integer epsgCode = getEpsgCodeFromEsriWkid(wkid);
                    if (epsgCode != null) {
                        try {
                            Field f = AbstractIdentifiedObject.class.getDeclaredField("identifiers");
                            ReflectionUtils.setObjectsAccessible(f);
                            f.set(crs, Collections.singleton(new NamedIdentifier(Citations.fromName("EPSG"), epsgCode.toString())));
                        } catch (ReflectiveOperationException | SecurityException e) {
                            Logging.error(e);
                        }
                    }
                }
            }
            // Find math transformation
            transform = CRS.findMathTransform(crs, wgs84);
        } catch (OperationNotFoundException e) {
            Logging.info(crs.getName()+": "+e.getMessage()); // Bursa wolf parameters required.

            if (findSimiliarCrs) {
                List<CoordinateReferenceSystem> candidates = new ArrayList<>();

                // Find matching CRS with Bursa Wolf parameters in EPSG database
                for (String code : CRS.getAuthorityFactory(false).getAuthorityCodes(ProjectedCRS.class)) {
                    try {
                        CoordinateReferenceSystem candidate = CRS.decode(code);
                        if (candidate instanceof AbstractCRS && crs instanceof AbstractIdentifiedObject) {

                            Hints.putSystemDefault(Hints.COMPARISON_TOLERANCE, Config.getPref().getDouble(
                                    OdConstants.PREF_CRS_COMPARISON_TOLERANCE, OdConstants.DEFAULT_CRS_COMPARISON_TOLERANCE));
                            if (((AbstractCRS) candidate).equals((AbstractIdentifiedObject) crs, false)) {
                                Logging.info("Found a potential CRS: "+candidate.getName());
                                candidates.add(candidate);
                            } else if (Config.getPref().getBoolean(OdConstants.PREF_CRS_COMPARISON_DEBUG, false)) {
                                compareDebug(crs, candidate);
                            }
                            Hints.removeSystemDefault(Hints.COMPARISON_TOLERANCE);
                        }
                    } catch (FactoryException ex) {
                        Logging.trace(ex);
                    }
                }

                if (candidates.size() > 1) {
                    Logging.warn("Found several potential CRS: "+Arrays.toString(candidates.toArray()));
                    // TODO: ask user which one to use
                }

                if (!candidates.isEmpty()) {
                    CoordinateReferenceSystem newCRS = candidates.get(0);
                    try {
                        transform = CRS.findMathTransform(newCRS, wgs84, false);
                    } catch (OperationNotFoundException ex) {
                        Logging.warn(newCRS.getName()+": "+e.getMessage());
                    }
                }
            }

            if (transform == null) {
                if (handler != null) {
                    // ask handler if it can provide a math transform
                    try {
                        transform = handler.findMathTransform(crs, wgs84, false);
                    } catch (OperationNotFoundException ex) {
                        Logging.warn(crs.getName()+": "+ex.getMessage()); // Bursa wolf parameters required.
                    }
                } else {
                    // ask default known handlers
                    for (GeographicHandler geoHandler : defaultHandlers) {
                        try {
                            if ((transform = geoHandler.findMathTransform(crs, wgs84, false)) != null) {
                                break;
                            }
                        } catch (OperationNotFoundException ex) {
                            Logging.warn(crs.getName()+": "+ex.getMessage()); // Bursa wolf parameters required.
                        }
                    }
                }
                if (transform == null) {
                    // ask user before trying lenient method, unless in headless mode (unit tests)
                    if (!GraphicsEnvironment.isHeadless() && warnLenientMethod(parent, crs)) {
                        // User canceled
                        throw new UserCancelException();
                    }
                    Logging.info("Searching for a lenient math transform.");
                    transform = CRS.findMathTransform(crs, wgs84, true);
                }
            }
        }
        if (transform == null) {
            throw new GeoMathTransformException("Unable to find math transform !");
        }
    }
}
