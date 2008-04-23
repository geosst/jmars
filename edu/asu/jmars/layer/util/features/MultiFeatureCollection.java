// Copyright 2008, Arizona Board of Regents
// on behalf of Arizona State University
// 
// Prepared by the Mars Space Flight Facility, Arizona State University,
// Tempe, AZ.
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.


package edu.asu.jmars.layer.util.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
// import edu.asu.jmars.util.Versionable;
// import edu.asu.jmars.util.Versionable;

/**
 * An implementation of FeatureCollection which provides an aggregate
 * homogenous view of multiple FeatureCollections. The list of FeatureCollections
 * making up this MultiFeatureCollection is called its <em>support</em>. The
 * MultiFeatureCollection listens to and forwards all the events generated by
 * its support. It also generates some of its own events to mimic behavior of
 * a FeatureCollection.
 * 
 * The schema of a MultiFeatureCollection is the aggregate of the schemas of
 * the support. Adding/removing support elements may result in change to the
 * schema.
 * 
 * The user can mark one FeatureCollection as the <em>default</em> for 
 * {@linkplain #addFeature(Feature)}/{@linkplain #addFeatures(Collection)}.
 * Using these methods without setting a default will generate exceptions.
 * 
 * NOTE:
 * Events from addition/removal/update to the owner FeatureCollection
 * are generated by that collection and are trapped by the MultiFeatureCollection.
 * The same are forwarded to any listeners listening to the MultiFeatureCollection.
 * 
 * As a general rule, operations are proxied to the owner FeatureCollections of
 * the Features. These subordinate FeatureCollections are listened to and any
 * local changes are made to the data structures based on the events generated 
 * by the owner FeatureCollections. 
 * 
 * @author saadat
 *
 */
public class MultiFeatureCollection implements FeatureCollection, FeatureListener {
	private static DebugLog log = DebugLog.instance();
	
	/**
	 * Backing list of FeatureCollections.
	 */
	private List support = new ArrayList();
	/**
	 * Backing list of Features from the entire support FeatureCollections.
	 */
	private List features = new ArrayList();
	private List schema = new LinkedList();
	private LinkedList listeners = new LinkedList();
	private FeatureCollection defaultFeatureCollection = null;
	
	
	//  TODO Versionable stuff 
	//private History history = null;
	//private Map fieldUndos = new HashMap();
	//private Map featureUndos = new HashMap();

	/**
	 * Sets the default FeatureCollection that will consume the 
	 * addFeature/addFeatures requests. This default can be null
	 * and it can be a FeatureCollection which is not already 
	 * a part of the support.
	 * 
	 * @param fc Default FeatureCollection (may be null).
	 * @see #addFeature(Feature)
	 * @see #addFeatures(Collection)
	 * 
	 */
	public void setDefaultFeatureCollection(FeatureCollection fc){
		defaultFeatureCollection = fc;
	}
	
	/**
	 * Returns the currently set default FeatureCollection.
	 * 
	 * @return Default FeatureCollection.
	 */
	public FeatureCollection getDefaultFeatureCollection(){
		return defaultFeatureCollection;
	}

	/**
	 * Add the given FeatureCollection to this MultiFeatureCollection.
	 * This FeatureCollection will become a part of the support.
	 * 
	 * @param fc FeatureCollection to add.
	 * @throws IllegalArgumentException if the input FeatureCollection is already 
	 *         a part of the support of this MultiFeatureCollection.
	 */
	public void addFeatureCollection(FeatureCollection fc){
		// This test has to be here in order for the removeFeatureCollection to work correctly.
		if (support.contains(fc))
			throw new IllegalArgumentException("FeatureCollection is already a part of the support.");
		
		support.add(fc);
		features.addAll(fc.getFeatures());
		
		LinkedHashSet currSchema = new LinkedHashSet(schema);
		Set oldSchema = new LinkedHashSet(currSchema);
		currSchema.addAll(fc.getSchema());
		Set diffSchema = new LinkedHashSet(currSchema);
		diffSchema.removeAll(oldSchema);
		schema = new LinkedList(currSchema);
		
		// Add the MultiFeatureCollection as a listener to this supporting FeatureCollection.
		fc.addListener(this);
		
		// Generate appropriate add Schema MFC event.
		if (!diffSchema.isEmpty()){
			FeatureEvent fe = new FeatureEvent(FeatureEvent.ADD_FIELD, this, null, null, new ArrayList(diffSchema));
			notify(fe);
		}
		// Generate appropriate add Feature MFC event.
		if (!fc.getFeatures().isEmpty()){
			FeatureEvent fe = new FeatureEvent(FeatureEvent.ADD_FEATURE, this, fc.getFeatures(), null, null);
			notify(fe);
		}
	}
	
	/**
	 * Remove the given FeatureCollection from this MultiFeatureCollection.
	 * 
	 * @param fc FeatureCollection to remove.
	 * @return true if the FeatureCollection was found and removed.
	 */
	public boolean removeFeatureCollection(FeatureCollection fc){
		FeatureEvent feFeatures = null;
		FeatureEvent feSchema = null;

		if (fc.getFeatureCount() > 0)
			feFeatures = new FeatureEvent(FeatureEvent.REMOVE_FEATURE, this, fc.getFeatures(), null, null);
		
		boolean result = support.remove(fc);

		// ArrayList.removeAll() is slooooooow hence features.removeAll() has been replaced
		// by the following code.
		ArrayList updated = new ArrayList(features.size());
		Set removeSet = new HashSet(fc.getFeatures());
		for(Iterator fi=features.iterator(); fi.hasNext(); ){
			Feature feat = (Feature)fi.next();
			if (!removeSet.contains(feat))
				updated.add(feat);
		}
		features = updated;
		
		Set diffSchema = new LinkedHashSet(schema);
		diffSchema.removeAll(getSchemaFor(support));
		if (!diffSchema.isEmpty())
			feSchema = new FeatureEvent(FeatureEvent.REMOVE_FIELD, this, null, null, new ArrayList(diffSchema)); 
		Set newSchema = new LinkedHashSet(schema);
		newSchema.removeAll(diffSchema);
		schema = new LinkedList(newSchema);
		
		// Remove the MultiFeatureCollection as a listener from this supporting FeatureCollection.
		fc.removeListener(this);
		
		// Generate appropriate remove Feature MFC event.
		if (feFeatures != null)
			notify(feFeatures);
		
		// Generate appropriate remove Schema MFC event.
		if (feSchema != null)
			notify(feSchema);
		
		return result;
	}
	
	/**
	 * Returns the support of this MultiFeatureCollection. In other words,
	 * it will return the FeatureCollections backing this MultiFeatureCollection.
	 * 
	 * @return A List of FeatureCollections backing this MultiFeatureCollections.
	 */
	public List getSupportingFeatureCollections(){
		return Collections.unmodifiableList(support);
	}

	/**
	 * Compute the aggregate schema for the given collection of FeatureCollections.
	 * 
	 * @return Aggregate schema as a Set of Field objects.
	 */
	private Set getSchemaFor(Collection fcc){
		Set schema = new HashSet();
		for(Iterator i=fcc.iterator(); i.hasNext(); ){
			FeatureCollection fc = (FeatureCollection)i.next();
			schema.addAll(fc.getSchema());
		}
		return schema;
	}
	
	/**
	 * Returns an unmodifiable List of Features that are a part of this
	 * MultiFeatureCollection. This List is constructed in order from the
	 * Feature objects that make up the support of this MultiFeatureCollection.
	 * 
	 * @return An unmodifiable List of Features making up this MultiFeatureCollection. 
	 */
	public List getFeatures(){
		return Collections.unmodifiableList(features);
	}
	
	/**
	 * Returns the count of Featues that are a part of this MultiFeatureCollection.
	 * This is equivalent to getFeatures().size().
	 * 
	 * @return Number of elements.
	 */
	public int getFeatureCount(){
		return features.size();
	}
	
	/**
	 * Adds the specified Feature to this MultiFeatureCollection. The addition is
	 * in fact proxied to the default FeatureCollection if one is set, otherwise
	 * it generates an UnsupportedOperationException.
	 * 
	 * @throws UnsupportedOperationException When the default FeatureCollection is null.
	 */
	public void addFeature(Feature f){
		if (defaultFeatureCollection == null)
			throw new UnsupportedOperationException("addFeature() called with no defaultFeatureCollection set.");
		
		defaultFeatureCollection.addFeature(f);
		// The FeatureEvent generated by the defaultFeatureCollection will cause the Feature
		// to be added to the features list in the MultiFeatureCollection.
	}

	/**
	 * Adds the specified collection of Feature objects to this MultiFeatureCollection.
	 * The addition is in fact proxied to the default FeatureCollection if one is set,
	 * otherwise it generates an UnsupportedOperationException.
	 * 
	 * @throws UnsupportedOperationException When the default FeatureCollection is null.
	 */
	public void addFeatures(Collection fc){
		if (defaultFeatureCollection == null)
			throw new UnsupportedOperationException("addFeatures() called with no defaultFeatureCollection set.");
		
		defaultFeatureCollection.addFeatures(fc);
		// The FeatureEvent generated by the defaultFeatureCollection will cause the Feature
		// to be added to the features list in the MultiFeatureCollection.
	}
	
	/**
	 * Removes the Feature from this MultiFeatureCollection. The removal is actually
	 * performed on the owner of the Feature.
	 * 
	 * @see Feature#getOwner()
	 */
	public void removeFeature(Feature f){
		f.getOwner().removeFeature(f);
		// The resulting FeatureEvent will cause the Feature to be removed from the
		// features list in the MultiFeatureCollection.
	}
	
	/**
	 * Removes a collection of Features from this MultiFeatureCollection.
	 * The removal is actually done from the respective owners of each of the
	 * Feature objects. The removal process is done in batches. A batch 
	 * consists of all the Feature objects that have the same owner.
	 * 
	 * @param c A collection of Features to remove.
	 */
	public void removeFeatures(Collection c){
		Map fcToFeat = new HashMap();
		Feature f;
		Collection fc;
		
		// Organize Features in batches
		for(Iterator i=c.iterator(); i.hasNext(); ){
			f = (Feature)i.next();
			fc = (Collection)fcToFeat.get(f.getOwner());
			if (fc == null)
				fcToFeat.put(f.getOwner(), fc = new LinkedList());
			fc.add(f);
		}
		
		// Remove Features in batches
		for(Iterator i=fcToFeat.entrySet().iterator(); i.hasNext(); ){
			Map.Entry e = (Map.Entry)i.next();
			((FeatureCollection)e.getKey()).removeFeatures((Collection)e.getValue());
		}
		// The resulting FeatureEvent will cause the Feature to be removed from the
		// features list in the MultiFeatureCollection.
	}

	/**
	 * Returns the index/position of the given Feature in the MultiFeatureCollection.
	 * 
	 * @param f Feature.
	 * @return Index/position of the Feature in the MultiFeatureCollection or 
	 *         -1 if the Feature is not found.
	 */
	public int featurePosition(Feature f){
		return features.indexOf(f);
	}
	
	/**
	 * Return the Feature located at the given index. This operation
	 * is equivalent to getFeatures().get(pos).
	 * 
	 * @param pos Position of the Feature within the MultiFeatureCollection.
	 * @return Feature object at the given position or null on an invalid index.
	 * 
	 * @see #getFeatures()
	 */
	public Feature getFeature(int pos){
		if (pos < 0 || pos >= features.size())
			return null;
		return (Feature)features.get(pos);
	}
	
	/**
	 * Replace the Feature at the given position with the new Feature
	 * provided. This method will proxy the replace to the appropriate
	 * FeatureCollection containing the Feature being replaced. No
	 * change takes place, if the specified position is invalid.
	 * 
	 * @param pos Position in the MultiFeatureCollection for replacement.
	 * @param f Replacement Feature object.
	 */
	public void setFeature(int pos, Feature f){
		if (pos < 0 || pos >= features.size())
			return;
		
		Feature old = (Feature)features.get(pos);
		features.set(pos, f);
		old.getOwner().setFeature(old.getOwner().featurePosition(old), f);
	}
	
	/**
	 * Move Features up/down by a certial number of indices.
	 * 
	 * @param at Current location of Features.
	 * @param n  Move amount, negative for move up, positive for move down.
	 * @return true if the move occurred, false otherwise.
	 */
	public boolean move(int[] at, int n){
		if (at.length == 0 || n == 0)
			return false;
		
		int[] idx = (int[])at.clone();
		Arrays.sort(idx);
		
		// Check if the move will push elements beyond limit
		if ((idx[0]+n) < 0 || (idx[idx.length-1]+n) >= features.size())
			return false;
		
		FeatureEvent fe;
		List fc = new ArrayList(idx.length);
		for(int i=idx.length-1; i>=0; i--)
			fc.add((Feature)features.get(idx[i]));
		fe = new FeatureEvent(FeatureEvent.REMOVE_FEATURE, this, fc, null, null);
		features.removeAll(fc);
		notify(fe);

		for(int i=0; i<idx.length; i++)
			features.add(idx[i]+n, (Feature)fc.get(i));
		fe = new FeatureEvent(FeatureEvent.ADD_FEATURE, this, fc, null, null);
		notify(fe);
		
		return true;
	}
	
	public boolean move(int[] at, boolean top){
		if (at.length == 0)
			return false;
		
		int[] idx = (int[])at.clone();
		Arrays.sort(idx);
		
		List fc = new ArrayList(idx.length);
		FeatureEvent fe;
		for(int i=idx.length-1; i>=0; i--)
			fc.add((Feature)features.get(idx[i]));
		fe = new FeatureEvent(FeatureEvent.REMOVE_FEATURE, this, fc, null, null);
		features.removeAll(fc);
		notify(fe);
		
		for(int i=0; i<idx.length; i++)
			features.add(top? i: features.size(), (Feature)fc.get(i));
		fe = new FeatureEvent (FeatureEvent.ADD_FEATURE, this, fc, null, null);
		notify(fe);
		
		return true;
	}
	
	
	/**
	 * Return a ListIterator over an unmodifiable list of Feature objects
	 * which are a part of the support of this MultiFeatureCollection.
	 * 
	 * TODO ConcurrentModificationException
	 * 
	 * @return A ListIterator over all the Feature objects in this
	 *         MultiFeatureCollection.
	 * 
	 */
	public ListIterator featureIterator(){
		return Collections.unmodifiableList(features).listIterator();
	}
	
	/**
	 * Returns the aggregate schema over the entire collection of FeatureCollections
	 * contained in this MultiFeatureCollection. The schema is returned as an 
	 * unmodifiable List.
	 * 
	 * @return The aggregate schema over the entire support of this MultiFeatureCollection
	 *         as an unmodifiable List.
	 */
	public List getSchema(){
		return Collections.unmodifiableList(schema);
	}
	
	/**
	 * Add the specified Field to all the FeatureCollections supporting this 
	 * MultiFeatureCollection.
	 * 
	 * NOTE:
	 * The add field messages flow back from the various FeatureCollections
	 * and at that point the local aggregate schema is updated.
	 * 
	 * @param field Field to add to the schema.
	 */
	public void addField(Field field){
		for(Iterator i=support.iterator(); i.hasNext(); ){
			FeatureCollection fc = (FeatureCollection)i.next();
			fc.addField(field);
		}
	}
	
	/**
	 * Remove the specified Field from the FeatureCollections supporting
	 * this MultiFeatureCollection.
	 * 
	 * @param field Field to remove from the Schema.
	 */
	public void removeField(Field field){
		for(Iterator i=support.iterator(); i.hasNext(); ){
			FeatureCollection fc = (FeatureCollection)i.next();
			fc.removeField(field);
		}
	}
	
	/**
	 * Returns null.
	 * @return null.
	 */
	public FeatureProvider getProvider(){
		return null;
	}

	/**
	 * Throws UnsupportedOperationException.
	 * 
	 * @param FeatureProvider The FeatureProvider that can produce/consume this
	 *        FeatureCollection.
	 *        
	 * @throws UnsupportedOpererationException always.
	 */
	public void setProvider(FeatureProvider provider){
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Makes changes to the Features stored in the MultiFeatureCollection
	 * as per the input map of Feature -&gt Field -&gt Value.
	 * All changes are proxied through the Feature owners.
	 * 
	 * @param features A map of Feature to a map of Field to Value.
	 */
	public void setAttributes(Map features){
		Map batches = new LinkedHashMap();
		Map batch;
		for(Iterator i=features.entrySet().iterator(); i.hasNext(); ){
			Map.Entry ei = (Map.Entry)i.next();
			Feature feat = (Feature)ei.getKey();
			if ((batch = (Map)batches.get(feat.getOwner())) == null)
				batches.put(feat.getOwner(), batch = new LinkedHashMap());
			batch.put(feat, ei.getValue());
		}
		
		for(Iterator i=batches.entrySet().iterator(); i.hasNext(); ){
			Map.Entry ei = (Map.Entry)i.next();
			((FeatureCollection)ei.getKey()).setAttributes((Map)ei.getValue());
		}
	}
	
	/**
	 * Make changes to the specified Fields of a particular Feature.
	 * 
	 * @param feature Feature to modify.
	 * @param fields Field -&gt Value map.
	 * 
	 */
	public void setAttributes(Feature feature, Map fields){
		if (feature.getOwner() == null)
			log.println("Ignoring orphan Feature.");
		else
			feature.getOwner().setAttributes(feature, fields);
	}
	
	/**
	 * Make changes to a particular Field for a collection of Features.
	 * 
	 * @param field Field to modify within the given Features.
	 * @param features Feature -&gt Value map.
	 */
	public void setAttributes(Field field, Map features){
		Map batches = new LinkedHashMap();
		Map batch;
		for(Iterator i=features.entrySet().iterator(); i.hasNext(); ){
			Map.Entry e = (Map.Entry)i.next();
			Feature feat = (Feature)e.getKey();
			
			if ((batch = (Map)batches.get(feat.getOwner())) == null)
				batches.put(feat.getOwner(), batch = new LinkedHashMap());
			batch.put(feat, e.getValue());
		}
		
		for(Iterator i=batches.entrySet().iterator(); i.hasNext(); ){
			Map.Entry e = (Map.Entry)i.next();
			if (e.getKey() == null)
				log.println("Ignoring "+((Map)e.getValue()).size()+" orphan Features.");
			else
				((FeatureCollection)e.getKey()).setAttributes(field, (Map)e.getValue());
		}
	}
	
	/**
	 * Adds a FeatureEvent listener to this MultiFeatureCollection.
	 * 
	 * @param l FeatureListener to add.
	 */
	public void addListener(FeatureListener l){
		listeners.add(l);
	}
	
	/**
	 * Removes a FeatureEvent listener from this MultiFeatureCollection.
	 * 
	 * @param l FeatureListener to remove.
	 */
	public void removeListener(FeatureListener l){
		listeners.remove(l);
	}
	
	/**
	 * Returns a List of all the FeatureListeners registered with this
	 * MultiFeatureCollection.
	 * 
	 * @return An unmodifiable list of FeatureListeners registered with
	 *         this MultiFeatureCollection.
	 */
	public List getListeners(){
		return Collections.unmodifiableList(listeners);
	}
	
	/**
	 * A generic FeatureEvent notification mechanism that notifies all
	 * the FeatureListeners registered with this MultiFeatureCollection.
	 * 
	 * @param e The FeatureEvent to send to all listeners.
	 */
	public void notify(FeatureEvent e){
		FeatureListener l;
		
		for(Iterator i=listeners.iterator(); i.hasNext(); ){
			l = (FeatureListener)i.next();
			l.receive(e);
		}
	}
	

	/**
	 * Handle events sent by SingleFeatureCollections and proxies
	 * them forward. This method implements the FeatureListener
	 * interface. The MultiFeatureCollection listens to all the 
	 * subordinate FeatureCollections that are the support of this
	 * MultiFeatureCollection.
	 * 
	 * Schema update events are processed here to update the current
	 * schema. The reason behind having a pre-computed schema is that
	 * clients of FeatureCollection expect the schema to be somewhat
	 * stable in its ordering. That is, addition to the schema occur
	 * at the tail end of the List of schema entries.
	 * 
	 * @param e FeatureEvent to process and pass along.
	 */
	public void receive(FeatureEvent e) {
		Set oldSchema, diffSchema, newSchema;
		FeatureEvent fe = null;
		
		switch(e.type){
		case FeatureEvent.ADD_FEATURE:
			features.addAll(e.features);
			// TODO Versionable - Process any outstanding Field FeatureEvents.
			//processFeatureUndos(e.features);
			// Forward the event by moving the indices into the MFC space.
			fe = new FeatureEvent(FeatureEvent.ADD_FEATURE, this, e.features, null, null);
			notify(fe);
			break;
		case FeatureEvent.REMOVE_FEATURE:
			// Forward the event by moving the indices into the MFC space
			fe = new FeatureEvent(FeatureEvent.REMOVE_FEATURE, this, e.features, null, null);
			Set toRemove = (e.features instanceof Set ? (Set)e.features : new HashSet(e.features));
			ArrayList newList = new ArrayList(features.size()-toRemove.size());
			for(Iterator fi=features.iterator(); fi.hasNext(); ){
				Feature f = (Feature)fi.next();
				if (!toRemove.contains(f))
					newList.add(f);
			}
			features = newList;
			notify(fe);
			break;
		case FeatureEvent.CHANGE_FEATURE:
		default:
			fe = new FeatureEvent(FeatureEvent.CHANGE_FEATURE, this, e.features,
					e.valuesBefore, e.fields);
			notify(fe);
			break;
			
		case FeatureEvent.ADD_FIELD:
			oldSchema = new HashSet(schema);
			newSchema = new LinkedHashSet(schema);
			newSchema.addAll(e.fields);
			schema = new LinkedList(newSchema);
			// TODO Versionable - Process any outstanding schema FieldEvents.
			//processFieldUndos(e.fields);
			diffSchema = new HashSet(schema);
			diffSchema.removeAll(oldSchema);
			
			if (!diffSchema.isEmpty()){
				fe = new FeatureEvent(FeatureEvent.ADD_FIELD, this, null, null, new ArrayList(diffSchema));
				notify(fe);
			}
			break;
			
		case FeatureEvent.REMOVE_FIELD:
			diffSchema = new HashSet(schema);
			diffSchema.removeAll(getSchemaFor(support));
			if (!diffSchema.isEmpty())
				fe = new FeatureEvent(FeatureEvent.REMOVE_FIELD, this, null, null, new ArrayList(diffSchema));
			newSchema = new LinkedHashSet(schema);
			newSchema.removeAll(diffSchema);
			schema = new LinkedList(newSchema);
			
			if (fe != null)
				notify(fe);
			break;
		}
	}
	
	/* TODO Versionable stuff
	private void processFeatureUndos(List features){
		for(Iterator fi=features.iterator(); fi.hasNext(); ){
			Feature feat = (Feature)fi.next();
			if (featureUndos.containsKey(feat)){
				FeatureEvent e = (FeatureEvent)featureUndos.get(feat);
				Integer index = (Integer)e.featureIndices.get(feat);
				if (index != null)
					move(new int[]{ featurePosition(feat) }, -index.intValue());
				
				// Remove outselves from the list of outstanding Feature undos
				featureUndos.remove(feat);
			}
		}
	}
	
	private void processFieldUndos(List fields){
		for(Iterator fi=fields.iterator(); fi.hasNext(); ){
			Field field = (Field)fi.next();
			if (fieldUndos.containsKey(field)){
				FeatureEvent e = (FeatureEvent)fieldUndos.get(field);
				Integer index = (Integer)e.fieldIndices.get(field);
				if (index != null)
					move(new int[]{ fieldPosition(field) }, -index.intValue());
				
				// Remove outselves from the list of outstanding Feature undos
				fieldUndos.remove(field);
			}
		}
	}
	*/

	/* TODO Versionable
	public void setHistory(History history) {
		this.history = history;
	}

	public void redo(Object obj) {
		// TODO Auto-generated method stub
		
	}

	public void undo(Object obj) {
		FeatureEvent e = (FeatureEvent)obj;
		switch(e.type){
		case FeatureEvent.ADD_FEATURE:
			// TODO Don't know how to handle move operations
			for(Iterator i=e.features.iterator(); i.hasNext(); )
				featureUndos.put(i.next(), e);
			break;
		case FeatureEvent.ADD_FIELD:
			/ *
			 * These will be handled by the underlying SingleFeatureCollection in case
			 * of an actual ADD_FEATURE/FIELD undo.
			 * These will be handled by the underlying FileTable when these were added
			 * due to a selection made in the FileTable which resulted in an 
			 * addFeatureCollection. 
			 * / 
			break;
		case FeatureEvent.REMOVE_FEATURE:
			// TODO Don't know how to handle move operations
			/ * 
			 * Undo operation in this case should restore the Feature to its original
			 * if the Feature is already a part of the collection. If not, this event
			 * should be added to outstanding events, which will be processed during
			 * receive().
			 * /
			for(Iterator i=e.features.iterator(); i.hasNext(); )
				featureUndos.put(i.next(), e);
			break;
		case FeatureEvent.REMOVE_FIELD:
			/ *
			 * Undo operation in this case should restore the File to its original
			 * if the File is already a part of the collection. If not, this event
			 * should be added to outstanding events, which will be processed during
			 * receive().
			 * /
			for(Iterator i=e.fields.iterator(); i.hasNext(); )
				fieldUndos.put(i.next(), e);
			break;
		default:
			log.println("Unhandled event "+e.type+" encountered.");
		}
	}
	*/

	/**
	 * For debugging use only. Constructs a textual representation of the object.
	 */
	public String debugToString(){
		List subs = new ArrayList();
		
		for(Iterator i=support.iterator(); i.hasNext(); ){
			FeatureCollection fc = (FeatureCollection)i.next(); 
			subs.add(fc.toString());
		}
		
		return "MultiFeatureCollection[default="+defaultFeatureCollection+
			"support=("+Util.join(",", (String[])subs.toArray(new String[0]))+")]";
	}

	private String fileName;
	public String getFilename() {
		return fileName;
	}
	public void setFilename(String fileName) {
		this.fileName = fileName;
	}
}
