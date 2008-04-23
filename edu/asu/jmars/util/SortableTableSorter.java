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


/**
 * A sorter for TableModels. The sorter has a model (conforming to TableModel)
 * and itself implements TableModel. TableSorter does not store or copy
 * the data in the TableModel, instead it maintains an array of
 * integers which it keeps the same size as the number of rows in its
 * model. When the model changes it notifies the sorter that something
 * has changed eg. "rowsAdded" so that its internal array of integers
 * can be reallocated. As requests are made of the sorter (like
 * getValueAt(row, col) it redirects them to its model via the mapping
 * array. That way the TableSorter appears to hold another copy of the table
 * with the rows in a different order. The sorting algorthm used is stable
 * which means that it does not move around rows when its comparison
 * function returns 0 to denote that they are equivalent.
 *
 * @version 1.5 12/17/97
 * @author Philip Milne
 *
 * Adapted for use in JMARS.  The only real change to the code was the implementation
 * of a secondary sort, which was (rather curiously) left half-way implemented.
 *
 * @author  Adaptation: James Winburn MSFF-ASU
 * @revision James Winburn MSFF-ASU 05/05
 *    added the unsortedIndexes array for swifter access of unsorted indices.
 * @revision James Winburn MSFF-ASU 10/05
 *    added map of keys to support unsorting of a custom TableModel.
 */
package edu.asu.jmars.util;

import java.util.Date;
import java.util.Vector;

import java.awt.event.*;
import javax.swing.table.*;
import javax.swing.*;
import javax.swing.event.*;
import gnu.trove.*;

    

public class SortableTableSorter extends AbstractTableModel implements TableModelListener 
{
    private static DebugLog log = DebugLog.instance();

    public int             indexes[];
    public int             unsortedIndexes[];
    Vector          sortingColumns  = new Vector();
    Vector          columnDirection = new Vector();
    TIntIntHashMap  keyMap          = null;


    // constructors

    public SortableTableSorter() {
        indexes = new int[0]; // for consistency
	unsortedIndexes = new int[0];
    }

    public SortableTableSorter(TableModel model) {
        setModel(model);
    }


    protected TableModel model;
    
    public TableModel getModel(){
	return model;
    }
    
    // By default, implement TableModel by forwarding all messages
    // to the model.
    
    
    public int getRowCount() {
	return (model == null) ? 0 : model.getRowCount();
    }
	
    public int getColumnCount() {
	return (model == null) ? 0 : model.getColumnCount();
    }
    
    public String getColumnName(int aColumn) {
	return (model == null) ? null : model.getColumnName(aColumn);
    }
	
    public Class getColumnClass(int aColumn) {
	return (model == null) ? null : model.getColumnClass(aColumn);
    }
	
    public boolean isCellEditable(int row, int column) {
	return (model == null) ? false : model.isCellEditable(row, column);
    }
	
    //
    // Implementation of the TableModelListener interface,
    //
    // By default forward all events to all the listeners.
    public void tableChanged(TableModelEvent e) {
	fireTableChanged(e);
    }



    /**
     * set the superclass's TableModel. Note that the
     * "getter" methods for the model are sensitive to the sorted 
     * state.
     */
    public void setModel(TableModel model) {
	this.model = model;
	model.addTableModelListener(this);
        reallocateIndexes();
    }

	Object valueSemaphore = new Object();

    public Object getValueAt(int aRow, int aColumn) {
	    synchronized( valueSemaphore){
		    checkModel();
		    //log.aprintln("row=" + aRow + " arraysize=" + indexes.length + " sort.rc=" + getRowCount() + " mod.rc=" + model.getRowCount() );
		    return model.getValueAt(indexes[aRow], aColumn);
	    }
    }

    public void setValueAt(Object aValue, int aRow, int aColumn) {
	    synchronized( valueSemaphore){
		    checkModel();
		    model.setValueAt(aValue, indexes[aRow], aColumn);
	    }
    }



    /**
     * If the rows of the table are changed in any way, the index array must 
     * be reset or sorting will be completely screwed.
     */
    public void reallocateIndexes() {
        int rowCount = model.getRowCount();

        // Set up a new array of indexes with the right number of elements
        // for the new data model.
        indexes = new int[rowCount];

        // Initialise with the identity mapping.
        for (int row = 0; row < rowCount; row++) {
            indexes[row] = row;
        }

        // Setup the unsorted array.
        unsortedIndexes = new int[rowCount];
        updateUnsortedIndexes();
    }




    /**
     * sort the rows of the table by the keys associated with the rows.
     * These keys are sent in as an argument.
     */
    public void sortByKey( TIntIntHashMap km){
	if (km==null){
	    this.keyMap = null;
	    return;
	}
	this.keyMap = km;
	sortByColumn( -1, true);
    }


    /** 
     * sort the table by the inputted column and in the inputted direction.
     */
    public void sortByColumn(int column, boolean ascending) {
        sortingColumns.removeAllElements();
        sortingColumns.addElement( new Integer(column));

        columnDirection.removeAllElements();
        columnDirection.addElement( new Boolean(ascending));

	sort();
        tableChanged(new TableModelEvent(this));
    }


    /**
     * sort the table by the inputted column and in the inputted direction, then
     * do a secondary sort by another column.
     */
    public void sortByColumn(int column1, boolean ascending1, int column2, boolean ascending2) {
        sortingColumns.removeAllElements();
        sortingColumns.addElement( new Integer(column1));
        sortingColumns.addElement( new Integer(column2));

        columnDirection.removeAllElements();
        columnDirection.addElement( new Boolean(ascending1));
        columnDirection.addElement( new Boolean(ascending2));

	sort();
        tableChanged(new TableModelEvent(this));
    }
   


    // the class's privates.



    // compare two rows of the table by the value of a column. 
    private int compareRowsByColumn(int row1, int row2, int column) 
    {
	if (column == -1){
	    // unsort by the key
	    if (keyMap==null || !keyMap.contains( row1) || !keyMap.contains(row2)){
		return 0;
	    }
	    int key1 = keyMap.get( row1);
	    int key2 = keyMap.get( row2);
            if (key1 < key2) {
                return -1;
            } else if (key1 > key2) {
                return 1;
            } else {
                return 0;
            }
	}  
	else {
	    // sort by the column

	    // Check for nulls.
	    Object o1 = model.getValueAt(row1, column);
	    Object o2 = model.getValueAt(row2, column);

	    // If both values are null, return 0.
	    if (o1 == null && o2 == null) {
		return 0;
	    } else if (o1 == null) { // Define null less than everything.
		return -1;
	    } else if (o2 == null) {
		return 1;
	    }
	    
	    /*
	     * We copy all returned values from the getValue call in case
	     * an optimised model is reusing one object to return many
	     * values.  The Number subclasses in the JDK are immutable and
	     * so will not be used in this way but other subclasses of
	     * Number might want to do this to save space and avoid
	     * unnecessary heap allocation.
	     */
	    Class type = model.getColumnClass(column);
	    if (type.getSuperclass() == java.lang.Number.class) {
		Number n1 = (Number)model.getValueAt(row1, column);
		double d1 = n1.doubleValue();
		Number n2 = (Number)model.getValueAt(row2, column);
		double d2 = n2.doubleValue();
		if (d1 < d2) {
		    return -1;
		} else if (d1 > d2) {
		    return 1;
		} else {
		    return 0;
		}
	    } else if (type == java.util.Date.class) {
		Date d1 = (Date)model.getValueAt(row1, column);
		long n1 = d1.getTime();
		Date d2 = (Date)model.getValueAt(row2, column);
		long n2 = d2.getTime();
		if (n1 < n2) {
		    return -1;
		} else if (n1 > n2) {
		    return 1;
		} else {
		    return 0;
		}
	    } else if (type == String.class) {
		String s1 = (String)model.getValueAt(row1, column);
		String s2 = (String)model.getValueAt(row2, column);
		int result = s1.compareTo(s2);
		if (result < 0) {
		    return -1;
		} else if (result > 0) {
		    return 1;
		} else {
		    return 0;
		}
	    } else if (type == Boolean.class) {
		Boolean bool1 = (Boolean)model.getValueAt(row1, column);
		boolean b1    = bool1.booleanValue();
		Boolean bool2 = (Boolean)model.getValueAt(row2, column);
		boolean b2    = bool2.booleanValue();
		if (b1 == b2) {
		    return 0;
		} else if (b1) { // Define false < true
		    return 1;
		} else {
		    return -1;
		}
	    } else {
		Object v1 = model.getValueAt(row1, column);
		String s1 = v1.toString();
		Object v2 = model.getValueAt(row2, column);
		String s2 = v2.toString();
		int result = s1.compareTo(s2);
		if (result < 0) {
		    return -1;
		} else if (result > 0) {
		    return 1;
		} else {
		    return 0;
		}
	    }
	}
    }

    private int compare(int row1, int row2) {
        for (int level = 0; level < sortingColumns.size(); level++) {
            Integer column    = (Integer)sortingColumns.elementAt(level);
	    boolean ascending = ((Boolean)columnDirection.elementAt(level)).booleanValue();
            int result = compareRowsByColumn(row1, row2, column.intValue());
            if (result != 0) {
                return ascending ? result : -result;
            }
        }
        return 0;
    }




    // Check that the indices correspond to the rows.
    private void checkModel() {
        if (indexes.length != model.getRowCount()) {
		log.aprintln("Sorting indices do NOT correspond to the rows.");
		log.aprintln("indexes.length=" + indexes.length + " model.rowcount=" + model.getRowCount());
        }
    }


    // sort the rows of the table by sorting the array of indices..
    private void sort() {
        checkModel();
	shuttlesort((int[])indexes.clone(), indexes, 0, indexes.length);
	updateUnsortedIndexes();
    }


    // update the sorting indices.
    private void updateUnsortedIndexes(){
	for (int i=0; i< indexes.length; i++){
	    unsortedIndexes[ indexes[i]] = i;
	}
    }


    // This is a home-grown implementation which we have not had time
    // to research - it may perform poorly in some circumstances. It
    // requires twice the space of an in-place algorithm and makes
    // NlogN assigments shuttling the values between the two
    // arrays. The number of compares appears to vary between N-1 and
    // NlogN depending on the initial order but the main reason for
    // using it here is that, unlike qsort, it is stable.
    private void shuttlesort(int from[], int to[], int low, int high) {
        if (high - low < 2) {
            return;
        }
        int middle = (low + high)/2;
        shuttlesort(to, from, low, middle);
        shuttlesort(to, from, middle, high);

        int p = low;
        int q = middle;

        /* This is an optional short-cut; at each recursive call,
        check to see if the elements in this subset are already
        ordered.  If so, no further comparisons are needed; the
        sub-array can just be copied.  The array must be copied rather
        than assigned otherwise sister calls in the recursion might
        get out of sinc.  When the number of elements is three they
        are partitioned so that the first set, [low, mid), has one
        element and and the second, [mid, high), has two. We skip the
        optimisation when the number of elements is three or less as
        the first compare in the normal merge will produce the same
        sequence of steps. This optimisation seems to be worthwhile
        for partially ordered lists but some analysis is needed to
        find out how the performance drops to Nlog(N) as the initial
        order diminishes - it may drop very quickly.  */

        if (high - low >= 4 && compare(from[middle-1], from[middle]) <= 0) {
            for (int i = low; i < high; i++) {
                to[i] = from[i];
            }
            return;
        }

        // A normal merge.
        for (int i = low; i < high; i++) {
            if (q >= high || (p < middle && compare(from[p], from[q]) <= 0)) {
                to[i] = from[p++];
            }
            else {
                to[i] = from[q++];
            }
        }
    }





}
