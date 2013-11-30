/**
 * Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
 *
 * This file is part of structr <http://structr.org>.
 *
 * structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.common;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.GenericNode;
import org.structr.core.entity.GenericRelationship;

/**
 * The default factory for unknown types in structr. When structr needs to
 * instantiate a node with an unknown / unregistered type, this class is
 * used.
 *
 * @author Christian Morgner
 */
public class DefaultFactoryDefinition implements FactoryDefinition {
	
	private static final Logger logger = Logger.getLogger(DefaultFactoryDefinition.class.getName());

	public static final String COMBINED_RELATIONSHIP_KEY_SEP = " ";
	
	public static final Class GENERIC_NODE_TYPE          = GenericNode.class;
	public static final Class GENERIC_REL_TYPE           = GenericRelationship.class;
	
	private String externalNodeTypeName = null;

	@Override
	public AbstractRelationship createGenericRelationship() {
		return new GenericRelationship();
	}

	@Override
	public Class getGenericRelationshipType() {
		return GENERIC_REL_TYPE;
	}

	@Override
	public AbstractNode createGenericNode() {
		return new GenericNode();
	}

	@Override
	public Class getGenericNodeType() {
		return GENERIC_NODE_TYPE;
	}

	@Override
	public boolean isGeneric(Class<?> entityClass) {
		
		return
		    GenericRelationship.class.isAssignableFrom(entityClass)
		    ||
		    GenericNode.class.isAssignableFrom(entityClass);
	}

	@Override
	public Class determineNodeType(final Node node) {
		
		final String type = GraphObject.type.dbName();
		if (node.hasProperty(type)) {
			
			final Object obj =  node.getProperty(type);
			if (obj != null) {
				
				Class nodeType = StructrApp.getConfiguration().getNodeEntities().get(obj.toString());
				if (nodeType != null) {
					
					return nodeType;
				}
			}
			
		} else {
			
			if (externalNodeTypeName == null) {
				
				// try to determine external node
				// type name from configuration
				externalNodeTypeName = StructrApp.getConfigurationValue(Services.FOREIGN_TYPE);
			}
			
			if (externalNodeTypeName != null && node.hasProperty(externalNodeTypeName)) {
				
				Object typeObj = node.getProperty(externalNodeTypeName);
				if (typeObj != null) {
					
					// String externalNodeType = typeObj.toString();
					
					// initialize dynamic type
					// genericNodeExtender.getType(externalNodeType);
					
					// return dynamic type
					Class dynamicType = StructrApp.getConfiguration().getNodeEntityClass(typeObj.toString());
					if (dynamicType != null) {
						
						return dynamicType;
					}
				}
			}
			
			
		}
		
		return getGenericNodeType();
	}

	@Override
	public Class determineRelationshipType(Relationship relationship) {

		final String type = GraphObject.type.dbName();
		
		// first try: duck-typing
		final String sourceType = relationship.getStartNode().hasProperty(type) ? relationship.getStartNode().getProperty(type).toString() : null;
		final String targetType = relationship.getEndNode().hasProperty(type) ? relationship.getEndNode().getProperty(type).toString() : null;
		final String relType    = relationship.getType().name();
		final Class entityType  = getClassForCombinedType(sourceType, relType, targetType);
		
		if (entityType != null) {
			logger.log(Level.FINE, "Class for assembled combined {0}", entityType.getName());
			return entityType;
		}

		// second try: type property
		if (relationship.hasProperty(type)) {
			
			Object obj =  relationship.getProperty(type);
			
			logger.log(Level.FINEST, "Type property: {0}", obj);
			
			if (obj != null) {
				
				Class relationClass = StructrApp.getConfiguration().getRelationshipEntityClass(obj.toString());
				if (relationClass != null) {
					StructrApp.getConfiguration().setRelationClassForCombinedType(sourceType, relType, targetType, relationClass);
					return relationClass;
				}
			}
		}

		// fallback to old type
		final String combinedTypeName = "combinedType";
		if (relationship.hasProperty(combinedTypeName)) {
			
			Object obj =  relationship.getProperty(combinedTypeName);
			
			logger.log(Level.FINE, "Combined type property: {0}", obj);
			
			if (obj != null) {
				
				Class classForCombinedType = getClassForCombinedType(obj.toString());
				if (classForCombinedType != null) {
					return classForCombinedType;
				}
			}
		}
		
		logger.log(Level.WARNING, "No instantiable class for relationship found for {0} {1} {2}, returning generic relationship class.", new Object[] { sourceType, relType, targetType });
		
		return getGenericRelationshipType();
	}

	private Class getClassForCombinedType(final String combinedType) {
		
		final String[] parts = StringUtils.split(combinedType, COMBINED_RELATIONSHIP_KEY_SEP);
		final String sourceType = parts[0];
		final String relType    = parts[1];
		final String targetType = parts[2];

		return getClassForCombinedType(sourceType, relType, targetType);
	}

	private Class getClassForCombinedType(final String sourceType, final String relType, final String targetType) {
		return StructrApp.getConfiguration().getRelationClassForCombinedType(sourceType, relType, targetType);
	}
}
