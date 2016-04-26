/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.media.core.configuration;

/**
 * Configuration parameters of an MGCP Endpoint.
 * 
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class MgcpEndpointConfiguration {
    
    public static int POOL_SIZE = 0;

    private String name;
    private String className;
    private int poolSize;

    public MgcpEndpointConfiguration() {
        this.name = "";
        this.className = "";
        this.poolSize = POOL_SIZE;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Endpoint name cannot be empty.");
        }
        this.name = name.toLowerCase();
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        if(className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Endpoint class name cannot be empty.");
        }
        this.className = className;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        if (poolSize < 0) {
            throw new IllegalArgumentException("Endpoint pool size cannot be negative.");
        }
        this.poolSize = poolSize;
    }

}
