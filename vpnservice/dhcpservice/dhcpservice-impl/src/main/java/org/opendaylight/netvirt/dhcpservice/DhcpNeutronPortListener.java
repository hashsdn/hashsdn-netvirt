/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpNeutronPortListener extends AsyncClusteredDataChangeListenerBase<Port, DhcpNeutronPortListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpNeutronPortListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private DataBroker broker;

    public DhcpNeutronPortListener(final DataBroker db, final DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(Port.class, DhcpNeutronPortListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.broker = db;
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DhcpNeutronPortListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.debug("DhcpNeutronPortListener Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port del) {
        LOG.trace("Port removed: {}", del);
        if(NeutronUtils.isPortVnicTypeNormal(del)) {
            return;
        }
        String macAddress = del.getMacAddress();
        Uuid networkId = del.getNetworkId();
        String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
        if (segmentationId == null) {
            return;
        }
        List<BigInteger> listOfDpns = DhcpServiceUtils.getListOfDpns(broker);
        dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(networkId.getValue(), listOfDpns, macAddress);
        dhcpExternalTunnelManager.removeVniMacToPortCache(new BigInteger(segmentationId), macAddress);
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        LOG.trace("Port changed to {}", update);
    }

    @Override
    protected void add(InstanceIdentifier<Port> identifier, Port add) {
        LOG.trace("Port added {}", add);
        if(NeutronUtils.isPortVnicTypeNormal(add)) {
            LOG.trace("Port is normal {}", add);
            return;
        }
        String macAddress = add.getMacAddress();
        Uuid networkId = add.getNetworkId();
        String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
        if (segmentationId == null) {
            LOG.trace("segmentation id is null");
            return;
        }
        dhcpExternalTunnelManager.updateVniMacToPortCache(new BigInteger(segmentationId), macAddress, add);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return DhcpNeutronPortListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.SUBTREE;
    }
}
