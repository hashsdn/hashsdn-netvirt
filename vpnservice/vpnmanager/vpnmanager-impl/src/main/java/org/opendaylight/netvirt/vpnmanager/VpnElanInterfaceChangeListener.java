/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnElanInterfaceChangeListener
    extends AsyncDataTreeChangeListenerBase<ElanInterface, VpnElanInterfaceChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnElanInterfaceChangeListener.class);

    private final DataBroker broker;
    private final IElanService elanService;

    public VpnElanInterfaceChangeListener(final DataBroker broker, final IElanService elanService) {
        super(ElanInterface.class, VpnElanInterfaceChangeListener.class);
        this.broker = broker;
        this.elanService = elanService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<ElanInterface> getWildCardPath() {
        return InstanceIdentifier.create(ElanInterfaces.class).child(ElanInterface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceName = elanInterface.getName();
        if (!elanService.isExternalInterface(interfaceName)) {
            LOG.debug("remove: Interface {} is not external. Ignoring interface removal", interfaceName);
            return;
        }

        if (!VpnUtil.isVpnInterfaceConfigured(broker, interfaceName)) {
            LOG.debug("remove: VpnInterface was never configured for {}. Ignoring interface removal", interfaceName);
            return;
        }

        InstanceIdentifier<VpnInterface> vpnInterfaceIdentifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        VpnUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vpnInterfaceIdentifier);
        LOG.info("remove: Removed VPN interface {}", interfaceName);
    }

    @Override
    protected void update(InstanceIdentifier<ElanInterface> key, ElanInterface origElanInterface,
        ElanInterface updatedElanInterface) {

    }

    @Override
    protected void add(InstanceIdentifier<ElanInterface> key, ElanInterface elanInterface) {
        String interfaceName = elanInterface.getName();
        if (!elanService.isExternalInterface(interfaceName)) {
            LOG.debug("add: Interface {} is not external. Ignoring", interfaceName);
            return;
        }

        Uuid networkId;
        try {
            networkId = new Uuid(elanInterface.getElanInstanceName());
        } catch (IllegalArgumentException e) {
            LOG.debug("add: ELAN instance {} for interface {} is not Uuid", elanInterface.getElanInstanceName(),
                    elanInterface.getName());
            return;
        }

        Uuid vpnId = VpnUtil.getExternalNetworkVpnId(broker, networkId);
        if (vpnId == null) {
            LOG.debug("add: Network {} is not external or vpn-id missing. Ignoring interface {} on elan {}",
                    networkId.getValue(), elanInterface.getName(), elanInterface.getElanInstanceName());
            return;
        }

        VpnInterface vpnInterface = VpnUtil.getVpnInterface(interfaceName, vpnId.getValue(), null, null, Boolean.FALSE);
        InstanceIdentifier<VpnInterface> vpnInterfaceIdentifier = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        VpnUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vpnInterfaceIdentifier, vpnInterface);
        LOG.info("add: Added VPN interface {} with VPN-id {} elanInstance {}", interfaceName, vpnId.getValue(),
                elanInterface.getElanInstanceName());
    }

    @Override
    protected VpnElanInterfaceChangeListener getDataTreeChangeListener() {
        return this;
    }
}
