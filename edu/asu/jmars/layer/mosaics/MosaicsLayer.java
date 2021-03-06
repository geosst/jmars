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


package edu.asu.jmars.layer.mosaics;

import java.util.HashSet;

import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureProvider;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.ObservableSet;

public class MosaicsLayer extends Layer {
	FeatureCollection fc;
	ObservableSet<Feature> selections = new ObservableSet<Feature>(new HashSet<Feature>());
	public MosaicsLayer(){
		FeatureProvider fp = new FeatureProviderWMS();
		fc = fp.load(Config.get("mosaics.server.url"));
	}
	
	public void receiveRequest(Object layerRequest, DataReceiver requester) {
		broadcast(fc.getFeatures());
	}
	
	public FeatureCollection getFeatures(){
		return fc;
	}
}
