/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.vpnmanager.VpnOpDataSyncer.VpnOpDataType;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.TaskState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortAddedToSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortRemovedFromSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterAssociatedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterDisassociatedFromVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetAddedToVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetAddedToVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetDeletedFromVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetDeletedFromVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.SubnetUpdatedInVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnSubnetRouteHandler implements NeutronvpnListener {
    private static final Logger LOG = LoggerFactory.getLogger(VpnSubnetRouteHandler.class);
    private final DataBroker dataBroker;
    private final SubnetOpDpnManager subOpDpnManager;
    private final IBgpManager bgpManager;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final IdManagerService idManager;
    private LockManagerService lockManager;
    private final VpnOpDataSyncer vpnOpDataSyncer;

    public VpnSubnetRouteHandler(final DataBroker dataBroker, final SubnetOpDpnManager subnetOpDpnManager,
        final IBgpManager bgpManager, final VpnInterfaceManager vpnIntfManager, final IdManagerService idManager,
        LockManagerService lockManagerService, final VpnOpDataSyncer vpnOpDataSyncer) {
        this.dataBroker = dataBroker;
        this.subOpDpnManager = subnetOpDpnManager;
        this.bgpManager = bgpManager;
        this.vpnInterfaceManager = vpnIntfManager;
        this.idManager = idManager;
        this.lockManager = lockManagerService;
        this.vpnOpDataSyncer = vpnOpDataSyncer;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onSubnetAddedToVpn(SubnetAddedToVpn notification) {
        if (!notification.isBgpVpn()) {
            return;
        }

        Uuid subnetId = notification.getSubnetId();
        String vpnName = notification.getVpnName();
        String subnetIp = notification.getSubnetIp();
        Long elanTag = notification.getElanTag();
        boolean isRouteAdvertised = false;

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        LOG.info("onSubnetAddedToVpn: Subnet {} being added to vpn", subnetId.getValue());
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == VpnConstants.INVALID_ID) {
            vpnOpDataSyncer.waitForVpnDataReady(VpnOpDataType.vpnInstanceToId, vpnName,
                VpnConstants.PER_VPN_INSTANCE_MAX_WAIT_TIME_IN_MILLISECONDS);
            vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            if (vpnId == VpnConstants.INVALID_ID) {
                LOG.error(
                    "onSubnetAddedToVpn: VpnInstance to VPNId mapping not yet available for VpnName {} processing "
                        + "subnet {} with IP {}, bailing out now.", vpnName, subnetId, subnetIp);
                return;
            }
        }
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                Subnetmap subMap = null;

                // Please check if subnetId belongs to an External Network
                InstanceIdentifier<Subnetmap> subMapid =
                    InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                        new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    LOG.error("onSubnetAddedToVpn: Unable to retrieve subnetmap entry for subnet : " + subnetId);
                    return;
                }
                subMap = sm.get();
                InstanceIdentifier<Networks> netsIdentifier =
                    InstanceIdentifier.builder(ExternalNetworks.class).child(Networks.class,
                        new NetworksKey(subMap.getNetworkId())).build();
                Optional<Networks> optionalNets =
                    VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
                if (optionalNets.isPresent()) {
                    LOG.info(
                        "onSubnetAddedToVpn: subnet {} is an external subnet on external network {}, so ignoring this"
                            + " for SubnetRoute", subnetId.getValue(), subMap.getNetworkId().getValue());
                    return;
                }
                //Create and add SubnetOpDataEntry object for this subnet to the SubnetOpData container
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (optionalSubs.isPresent()) {
                    LOG.error("onSubnetAddedToVpn: SubnetOpDataEntry for subnet {} already detected to be present",
                        subnetId.getValue());
                    return;
                }
                LOG.debug("onSubnetAddedToVpn: Creating new SubnetOpDataEntry node for subnet: " + subnetId.getValue());
                Map<BigInteger, SubnetToDpn> subDpnMap = new HashMap<>();
                BigInteger dpnId = null;
                BigInteger nhDpnId = null;
                SubnetToDpn subDpn = null;

                SubnetOpDataEntryBuilder subOpBuilder =
                    new SubnetOpDataEntryBuilder().setKey(new SubnetOpDataEntryKey(subnetId));
                subOpBuilder.setSubnetId(subnetId);
                subOpBuilder.setSubnetCidr(subnetIp);
                String primaryRd = VpnUtil.getPrimaryRd(dataBroker, vpnName);
                if (!VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                    LOG.error("onSubnetAddedToVpn: The VPN Instance name "
                            + notification.getVpnName() + " does not have RD ");
                    return;
                }
                subOpBuilder.setVrfId(primaryRd);
                subOpBuilder.setVpnName(vpnName);
                subOpBuilder.setSubnetToDpn(new ArrayList<>());
                subOpBuilder.setRouteAdvState(TaskState.Na);
                subOpBuilder.setElanTag(elanTag);

                // First recover set of ports available in this subnet
                List<Uuid> portList = subMap.getPortList();
                if (portList != null) {
                    for (Uuid port: portList) {
                        Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,port.getValue());
                        if (intfState != null) {
                            try {
                                dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                            } catch (Exception e) {
                                LOG.error("onSubnetAddedToVpn: Unable to obtain dpnId for interface {},",
                                        " subnetroute inclusion for this interface failed with exception {}",
                                        port.getValue(), e);
                                continue;
                            }
                            if (dpnId.equals(BigInteger.ZERO)) {
                                LOG.info("onSubnetAddedToVpn: Port " + port.getValue()
                                    + " is not assigned DPN yet, ignoring ");
                                continue;
                            }
                            subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, dpnId);
                            if (intfState.getOperStatus() != OperStatus.Up) {
                                LOG.info("onSubnetAddedToVpn: Port " + port.getValue() + " is not UP yet, ignoring ");
                                continue;
                            }
                            subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, port.getValue());
                            if (intfState.getOperStatus() == OperStatus.Up) {
                                // port is UP
                                subDpnMap.put(dpnId, subDpn);
                                if (nhDpnId == null) {
                                    nhDpnId = dpnId;
                                }
                            }
                        } else {
                            subOpDpnManager.addPortOpDataEntry(port.getValue(), subnetId, null);
                        }
                    }
                    if (subDpnMap.size() > 0) {
                        subOpBuilder.setSubnetToDpn(new ArrayList<>(subDpnMap.values()));
                    }
                }

                if (nhDpnId != null) {
                    LOG.info("Next-Hop dpn {} is available for rd {} subnetIp {} vpn {}", nhDpnId, primaryRd,
                        subnetIp, vpnName);
                    subOpBuilder.setNhDpnId(nhDpnId);
                    try {
                        /*
                        Write the subnet route entry to the FIB.
                        And also advertise the subnet route entry via BGP.
                        */
                        int label = getLabel(primaryRd, subnetIp);
                        if (label == 0) {
                            LOG.error(
                                "Unable to fetch label from Id Manager. Bailing out of handling addition of subnet {}"
                                    + " to vpn {}",
                                subnetIp, vpnName);
                            return;
                        }
                        isRouteAdvertised =
                            addSubnetRouteToFib(primaryRd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                        if (isRouteAdvertised) {
                            subOpBuilder.setRouteAdvState(TaskState.Done);
                        } else {
                            subOpBuilder.setNhDpnId(null);
                            subOpBuilder.setRouteAdvState(TaskState.Na);
                        }
                    } catch (Exception ex) {
                        LOG.error(
                            "onSubnetAddedToVpn: FIB rules and Advertising nhDpnId {} information for subnet {} to "
                                + "BGP failed",
                            nhDpnId, subnetId.getValue(), ex);
                        subOpBuilder.setRouteAdvState(TaskState.Pending);
                    }
                } else {
                    LOG.info("Next-Hop dpn is unavailable for rd {} subnetIp {} vpn {}", primaryRd, subnetIp, vpnName);
                }

                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onSubnetAddedToVpn: Added subnetopdataentry to OP Datastore for subnet {}",
                        subnetId.getValue());
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle subnet {} added to vpn {} {}", subnetIp, vpnName, e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onSubnetDeletedFromVpn(SubnetDeletedFromVpn notification) {
        Uuid subnetId = notification.getSubnetId();

        if (!notification.isBgpVpn()) {
            return;
        }
        LOG.info("onSubnetDeletedFromVpn: Subnet " + subnetId.getValue() + " being removed from vpn");
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                LOG.trace(" Removing the SubnetOpDataEntry node for subnet: " +  subnetId.getValue());
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onSubnetDeletedFromVpn: SubnetOpDataEntry for subnet {} not available in datastore",
                        subnetId.getValue());
                    return;
                }

                /* If subnet is deleted (or if its removed from VPN), the ports that are DOWN on that subnet
                 * will continue to be stale in portOpData DS, as subDpnList used for portOpData removal will
                 * contain only ports that are UP. So here we explicitly cleanup the ports of the subnet by
                 * going through the list of ports on the subnet
                 */
                InstanceIdentifier<Subnetmap> subMapid =
                    InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                        new SubnetmapKey(subnetId)).build();
                Optional<Subnetmap> sm = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subMapid);
                if (!sm.isPresent()) {
                    LOG.error("Stale ports removal: Unable to retrieve subnetmap entry for subnet : " + subnetId);
                } else {
                    Subnetmap subMap = sm.get();
                    List<Uuid> portList = subMap.getPortList();
                    if (portList != null) {
                        for (Uuid port : portList) {
                            InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                                InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                                    new PortOpDataEntryKey(port.getValue())).build();
                            LOG.trace("Deleting portOpData entry for port " + port.getValue());
                            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                        }
                    }
                }

                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
                LOG.info("onSubnetDeletedFromVpn: Removed subnetopdataentry for subnet {} successfully from Datastore",
                    subnetId.getValue());
                try {
                    //Withdraw the routes for all the interfaces on this subnet
                    //Remove subnet route entry from FIB
                    deleteSubnetRouteFromFib(rd, subnetIp, vpnName);
                } catch (Exception ex) {
                    LOG.error("onSubnetAddedToVpn: Withdrawing routes from BGP for subnet {} failed",
                        subnetId.getValue(), ex);
                }
            } catch (Exception ex) {
                LOG.error("Removal of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle subnet {} removed to vpn {}", notification.getSubnetIp(),
                notification.getVpnName(), e);
        }
    }

    @Override
    public void onSubnetUpdatedInVpn(SubnetUpdatedInVpn notification) {
        Uuid subnetId = notification.getSubnetId();
        String vpnName = notification.getVpnName();
        String subnetIp = notification.getSubnetIp();
        Long elanTag = notification.getElanTag();

        Preconditions.checkNotNull(subnetId, "SubnetId cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetPrefix cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "VpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "ElanTag cannot be null or empty!");

        InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
            InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                new SubnetOpDataEntryKey(subnetId)).build();
        Optional<SubnetOpDataEntry> optionalSubs =
            VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
        if (optionalSubs.isPresent()) {
            if (!notification.isBgpVpn()) {
                SubnetDeletedFromVpnBuilder bldr = new SubnetDeletedFromVpnBuilder().setVpnName(vpnName);
                bldr.setElanTag(elanTag).setBgpVpn(true).setSubnetIp(subnetIp).setSubnetId(subnetId);
                onSubnetDeletedFromVpn(bldr.build());
            }
            // TODO(vivek): Something got updated, but we donot know what ?
        } else {
            if (notification.isBgpVpn()) {
                SubnetAddedToVpnBuilder bldr = new SubnetAddedToVpnBuilder().setVpnName(vpnName).setElanTag(elanTag);
                bldr.setSubnetIp(subnetIp).setSubnetId(subnetId).setBgpVpn(true);
                onSubnetAddedToVpn(bldr.build());
            }
            // TODO(vivek): Something got updated, but we donot know what ?
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onPortAddedToSubnet(PortAddedToSubnet notification) {
        Uuid subnetId = notification.getSubnetId();
        Uuid portId = notification.getPortId();
        boolean isRouteAdvertised = false;

        LOG.info("onPortAddedToSubnet: Port " + portId.getValue() + " being added to subnet " + subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();

                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.info("onPortAddedToSubnet: Port {} is part of a subnet {} that is not in VPN, ignoring",
                        portId.getValue(), subnetId.getValue());
                    return;
                }
                Interface intfState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker,portId.getValue());
                if (intfState == null) {
                    // Interface State not yet available
                    subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, null);
                    return;
                }
                BigInteger dpnId = BigInteger.ZERO;
                try {
                    dpnId = InterfaceUtils.getDpIdFromInterface(intfState);
                } catch (Exception e) {
                    LOG.error("onSubnetAddedToVpn: Unable to obtain dpnId for interface {},",
                            " subnetroute inclusion for this interface failed with exception {}",
                            portId.getValue(), e);
                    return;
                }
                if (dpnId.equals(BigInteger.ZERO)) {
                    LOG.info("onPortAddedToSubnet: Port " + portId.getValue() + " is not assigned DPN yet, ignoring ");
                    return;
                }
                subOpDpnManager.addPortOpDataEntry(portId.getValue(), subnetId, dpnId);
                if (intfState.getOperStatus() != OperStatus.Up) {
                    LOG.info("onPortAddedToSubnet: Port " + portId.getValue() + " is not UP yet, ignoring ");
                    return;
                }
                LOG.debug("onPortAddedToSubnet: Updating the SubnetOpDataEntry node for subnet {}",
                    subnetId.getValue());
                SubnetToDpn subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, portId.getValue());
                if (subDpn == null) {
                    return;
                }
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getNhDpnId()  == null) {
                    subOpBuilder.setNhDpnId(dpnId);
                }
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                if ((subOpBuilder.getRouteAdvState() == TaskState.Pending)
                    || (subOpBuilder.getRouteAdvState() == TaskState.Na)) {
                    try {
                        // Write the Subnet Route Entry to FIB
                        // Advertise BGP Route here and set route_adv_state to DONE
                        int label = getLabel(rd, subnetIp);
                        if (label == 0) {
                            LOG.error(
                                "Unable to fetch label from Id Manager. Bailing out of handling addition of port {} "
                                    + "to subnet {} in vpn {}",
                                portId.getValue(), subnetIp, vpnName);
                            return;
                        }
                        isRouteAdvertised =
                            addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                        if (isRouteAdvertised) {
                            subOpBuilder.setRouteAdvState(TaskState.Done);
                        } else {
                            subOpBuilder.setNhDpnId(null);
                            subOpBuilder.setRouteAdvState(TaskState.Na);
                        }
                    } catch (Exception ex) {
                        LOG.error(
                            "onPortAddedToSubnet: Advertising NextHopDPN {} information for subnet {} to BGP failed",
                            nhDpnId, subnetId.getValue(), ex);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onPortAddedToSubnet: Updated subnetopdataentry to OP Datastore for port {}",
                    portId.getValue());

            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle port {} added to subnet {} {}", portId.getValue(), subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onPortRemovedFromSubnet(PortRemovedFromSubnet notification) {
        Uuid subnetId = notification.getSubnetId();
        Uuid portId = notification.getPortId();
        boolean isRouteAdvertised = false;

        LOG.info(
            "onPortRemovedFromSubnet: Port " + portId.getValue() + " being removed from subnet " + subnetId.getValue());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                PortOpDataEntry portOpEntry = subOpDpnManager.removePortOpDataEntry(portId.getValue());
                if (portOpEntry == null) {
                    return;
                }
                BigInteger dpnId = portOpEntry.getDpnId();
                if (dpnId == null) {
                    LOG.debug("onPortRemovedFromSubnet:  Port {} does not have a DPNId associated, ignoring",
                        portId.getValue());
                    return;
                }
                LOG.debug(
                    "onPortRemovedFromSubnet: Updating the SubnetOpDataEntry node for subnet: " + subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, portId.getValue());
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.info("onPortRemovedFromSubnet: Port {} is part of a subnet {} that is not in VPN, ignoring",
                        portId.getValue(), subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                List<SubnetToDpn> subDpnList = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        LOG.debug("onPortRemovedFromSubnet: Last port {} on the subnet {}", portId,
                            subnetId.getValue());
                        // last port on this DPN, so we need to swap the NHDpnId
                        subDpnList = subOpBuilder.getSubnetToDpn();
                        if (subDpnList.isEmpty()) {
                            subOpBuilder.setNhDpnId(null);
                            try {
                                // withdraw route from BGP
                                deleteSubnetRouteFromFib(rd, subnetIp, vpnName);
                                subOpBuilder.setRouteAdvState(TaskState.Na);
                            } catch (Exception ex) {
                                LOG.error(
                                    "onPortRemovedFromSubnet: Withdrawing NextHopDPN {} information for subnet {} "
                                        + "from BGP failed ",
                                    dpnId, subnetId.getValue(), ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        } else {
                            nhDpnId = subDpnList.get(0).getDpnId();
                            subOpBuilder.setNhDpnId(nhDpnId);
                            LOG.debug("onInterfaceDown: Swapping the Designated DPN to {} for subnet {}", nhDpnId,
                                subnetId.getValue());
                            try {
                                // Best effort Withdrawal of route from BGP for this subnet
                                // Advertise the new NexthopIP to BGP for this subnet
                                //withdrawSubnetRoutefromBgp(rd, subnetIp);
                                int label = getLabel(rd, subnetIp);
                                if (label == 0) {
                                    LOG.error(
                                        "Unable to fetch label from Id Manager. Bailing out of handling removal of "
                                            + "port {} from subnet {} in vpn {}",
                                        portId.getValue(), subnetIp, vpnName);
                                    return;
                                }
                                isRouteAdvertised =
                                    addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                                if (isRouteAdvertised) {
                                    subOpBuilder.setRouteAdvState(TaskState.Done);
                                } else {
                                    subOpBuilder.setNhDpnId(null);
                                    subOpBuilder.setRouteAdvState(TaskState.Na);
                                }
                            } catch (Exception ex) {
                                LOG.error(
                                    "onPortRemovedFromSubnet: Swapping Withdrawing NextHopDPN {} information for "
                                        + "subnet {} to BGP failed",
                                    dpnId, subnetId.getValue(), ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        }
                    }
                }
                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info(
                    "onPortRemovedFromSubnet: Updated subnetopdataentry to OP Datastore removing port " + portId
                        .getValue());
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle port {} removed from subnet {} {}", portId.getValue(), subnetId.getValue(),
                e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceUp(BigInteger dpnId, String intfName) {
        LOG.info("onInterfaceUp: Port " + intfName);
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        SubnetToDpn subDpn = null;
        boolean isRouteAdvertised = false;
        PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(intfName);
        if (portOpEntry == null) {
            LOG.info("onInterfaceUp: Port " + intfName + "is part of a subnet not in VPN, ignoring");
            return;
        }
        if ((dpnId == null) || (Objects.equals(dpnId, BigInteger.ZERO))) {
            dpnId = portOpEntry.getDpnId();
            if (dpnId == null) {
                LOG.error("onInterfaceUp: Unable to determine the DPNID for port " + intfName);
                return;
            }
        }
        Uuid subnetId = portOpEntry.getSubnetId();
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onInterfaceUp: SubnetOpDataEntry for subnet {} is not available", subnetId.getValue());
                    return;
                }

                LOG.debug("onInterfaceUp: Updating the SubnetOpDataEntry node for subnet: " + subnetId.getValue());
                subOpDpnManager.addPortOpDataEntry(intfName, subnetId, dpnId);
                subDpn = subOpDpnManager.addInterfaceToDpn(subnetId, dpnId, intfName);
                if (subDpn == null) {
                    return;
                }
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                subDpnList.add(subDpn);
                subOpBuilder.setSubnetToDpn(subDpnList);
                if (subOpBuilder.getNhDpnId() == null) {
                    subOpBuilder.setNhDpnId(dpnId);
                }
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                if ((subOpBuilder.getRouteAdvState() == TaskState.Pending)
                    || (subOpBuilder.getRouteAdvState() == TaskState.Na)) {
                    try {
                        // Write the Subnet Route Entry to FIB
                        // Advertise BGP Route here and set route_adv_state to DONE
                        int label = getLabel(rd, subnetIp);
                        if (label == 0) {
                            LOG.error(
                                "Unable to fetch label from Id Manager. Bailing out of handling interface up event "
                                    + "for port {} for subnet {} in vpn {}",
                                intfName, subnetIp, vpnName);
                            return;
                        }
                        isRouteAdvertised =
                            addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                        if (isRouteAdvertised) {
                            subOpBuilder.setRouteAdvState(TaskState.Done);
                        } else {
                            subOpBuilder.setNhDpnId(null);
                            subOpBuilder.setRouteAdvState(TaskState.Na);
                        }
                    } catch (Exception ex) {
                        LOG.error("onInterfaceUp: Advertising NextHopDPN {} information for subnet {} to BGP failed",
                            nhDpnId, subnetId.getValue(), ex);
                    }
                }
                SubnetOpDataEntry subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onInterfaceUp: Updated subnetopdataentry to OP Datastore port up " + intfName);
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle interface up event for port {} in subnet {} {}", portOpEntry.getPortId(),
                subnetId.getValue(), e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onInterfaceDown(final BigInteger dpnId, final String interfaceName) {
        boolean isRouteAdvertised = false;
        LOG.info("onInterfaceDown: Port " + interfaceName);
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        PortOpDataEntry portOpEntry = subOpDpnManager.getPortOpDataEntry(interfaceName);
        if (portOpEntry == null) {
            LOG.info("onInterfaceDown: Port " + interfaceName + "is part of a subnet not in VPN, ignoring");
            return;
        }
        if ((dpnId == null) || (Objects.equals(dpnId, BigInteger.ZERO))) {
            LOG.error("onInterfaceDown: Unable to determine the DPNID for port " + interfaceName);
            return;
        }
        Uuid subnetId = portOpEntry.getSubnetId();
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                LOG.debug("onInterfaceDown: Updating the SubnetOpDataEntry node for subnet: " + subnetId.getValue());
                boolean last = subOpDpnManager.removeInterfaceFromDpn(subnetId, dpnId, interfaceName);
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("onInterfaceDown: SubnetOpDataEntry for subnet {} is not available", subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                List<SubnetToDpn> subDpnList = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                Long elanTag = subOpBuilder.getElanTag();
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    // select another NhDpnId
                    if (last) {
                        LOG.debug(
                            "onInterfaceDown: Last active port " + interfaceName + " on the subnet: " + subnetId
                                .getValue());
                        // last port on this DPN, so we need to swap the NHDpnId
                        subDpnList = subOpBuilder.getSubnetToDpn();
                        if (subDpnList.isEmpty()) {
                            subOpBuilder.setNhDpnId(null);
                            try {
                                // Withdraw route from BGP for this subnet
                                deleteSubnetRouteFromFib(rd, subnetIp, vpnName);
                                subOpBuilder.setRouteAdvState(TaskState.Na);
                            } catch (Exception ex) {
                                LOG.error(
                                    "onInterfaceDown: Withdrawing NextHopDPN {} information for subnet {} from BGP "
                                        + "failed",
                                    dpnId, subnetId.getValue(), ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        } else {
                            nhDpnId = subDpnList.get(0).getDpnId();
                            subOpBuilder.setNhDpnId(nhDpnId);
                            LOG.debug(
                                "onInterfaceDown: Swapping the Designated DPN to {} for subnet {}" , nhDpnId,
                                    subnetId.getValue());
                            try {
                                // Best effort Withdrawal of route from BGP for this subnet
                                //withdrawSubnetRoutefromBgp(rd, subnetIp);
                                int label = getLabel(rd, subnetIp);
                                if (label == 0) {
                                    LOG.error(
                                        "Unable to fetch label from Id Manager. Bailing out of handling interface "
                                            + "down event for port {} in subnet {} for vpn {}",
                                        interfaceName, subnetIp, vpnName);
                                    return;
                                }
                                isRouteAdvertised =
                                    addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                                if (isRouteAdvertised) {
                                    subOpBuilder.setRouteAdvState(TaskState.Done);
                                } else {
                                    subOpBuilder.setNhDpnId(null);
                                    subOpBuilder.setRouteAdvState(TaskState.Na);
                                }
                            } catch (Exception ex) {
                                LOG.error(
                                    "onInterfaceDown: Swapping Withdrawing NextHopDPN {} information for "
                                        + "subnet {} to BGP failed", dpnId, subnetId.getValue(), ex);
                                subOpBuilder.setRouteAdvState(TaskState.Pending);
                            }
                        }
                    }
                }
                subOpEntry = subOpBuilder.build();
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                LOG.info("onInterfaceDown: Updated subnetopdataentry to OP Datastore port down " + interfaceName);
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle interface down event for port {} in subnet {} {}", portOpEntry.getPortId(),
                subnetId.getValue(), e);
        }
    }

    @Override
    public void onRouterAssociatedToVpn(RouterAssociatedToVpn notification) {
    }

    @Override
    public void onRouterDisassociatedFromVpn(RouterDisassociatedFromVpn notification) {
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelUpEvent(Uuid subnetId, BigInteger dpnId) {
        boolean isRouteAdvertised = false;
        LOG.info("updateSubnetRouteOnTunnelUpEvent: Subnet {} Dpn {}", subnetId.getValue(), dpnId.toString());
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("updateSubnetRouteOnTunnelUpEvent: SubnetOpDataEntry for subnet {} is not available",
                        subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                String rd = subOpBuilder.getVrfId();
                String subnetIp = subOpBuilder.getSubnetCidr();
                String vpnName = subOpBuilder.getVpnName();
                List<SubnetToDpn> subDpnList = subOpBuilder.getSubnetToDpn();
                long elanTag = subOpBuilder.getElanTag();
                if ((subOpBuilder.getRouteAdvState() == TaskState.Pending)
                    || (subOpBuilder.getRouteAdvState() == TaskState.Na)) {
                    for (SubnetToDpn subDpn : subDpnList) {
                        if (subDpn.getDpnId().equals(dpnId)) {
                            if (subOpBuilder.getNhDpnId() == null) {
                                try {
                                    subOpBuilder.setNhDpnId(dpnId);
                                    int label = getLabel(rd, subnetIp);
                                    isRouteAdvertised =
                                        addSubnetRouteToFib(rd, subnetIp, dpnId, vpnName, elanTag, label, subnetId);
                                    if (isRouteAdvertised) {
                                        subOpBuilder.setRouteAdvState(TaskState.Done);
                                    } else {
                                        subOpBuilder.setNhDpnId(null);
                                        subOpBuilder.setRouteAdvState(TaskState.Na);
                                    }
                                } catch (Exception ex) {
                                    LOG.error(
                                        "updateSubnetRouteOnTunnelUpEvent: Advertising NextHopDPN {} information for "
                                            + "subnet {} to BGP failed",
                                        dpnId, subnetId.getValue(), ex);
                                }
                            }
                        }
                    }
                    subOpEntry = subOpBuilder.build();
                    MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                    LOG.info(
                        "updateSubnetRouteOnTunnelUpEvent: Updated subnetopdataentry to OP Datastore tunnel up on dpn"
                            + " {} for subnet {}",
                        dpnId.toString(), subnetId.getValue());
                }
            } catch (Exception ex) {
                LOG.error("Creation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle tunnel up event for subnetId {} dpnId {}", subnetId.getValue(),
                dpnId.toString());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void updateSubnetRouteOnTunnelDownEvent(Uuid subnetId, BigInteger dpnId) {
        LOG.info("updateSubnetRouteOnTunnelDownEvent: Subnet {} Dpn {}", subnetId.getValue(), dpnId.toString());
        //TODO(vivek): Change this to use more granularized lock at subnetId level
        try {
            VpnUtil.lockSubnet(lockManager, subnetId.getValue());
            try {
                InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                    InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                        new SubnetOpDataEntryKey(subnetId)).build();
                Optional<SubnetOpDataEntry> optionalSubs = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL,
                    subOpIdentifier);
                if (!optionalSubs.isPresent()) {
                    LOG.error("updateSubnetRouteOnTunnelDownEvent: SubnetOpDataEntry for subnet {} is not available",
                        subnetId.getValue());
                    return;
                }
                SubnetOpDataEntry subOpEntry = null;
                SubnetOpDataEntryBuilder subOpBuilder = new SubnetOpDataEntryBuilder(optionalSubs.get());
                BigInteger nhDpnId = subOpBuilder.getNhDpnId();
                if ((nhDpnId != null) && (nhDpnId.equals(dpnId))) {
                    electNewDPNForSubNetRoute(subOpBuilder, dpnId, subnetId);
                    subOpEntry = subOpBuilder.build();
                    MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, subOpIdentifier, subOpEntry);
                    LOG.info(
                        "updateSubnetRouteOnTunnelDownEvent: Updated subnetopdataentry to OP Datastore tunnnel down "
                            + "on dpn {} for subnet {}",
                        dpnId.toString(), subnetId.getValue());
                }
            } catch (Exception ex) {
                LOG.error("Updation of SubnetOpDataEntry for subnet {} failed", subnetId.getValue(), ex);
            } finally {
                VpnUtil.unlockSubnet(lockManager, subnetId.getValue());
            }
        } catch (Exception e) {
            LOG.error("Unable to handle tunnel down event for subnetId {} dpnId {}", subnetId.getValue(),
                dpnId.toString());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean addSubnetRouteToFib(String rd, String subnetIp, BigInteger nhDpnId, String vpnName,
        Long elanTag, int label, Uuid subnetId) throws Exception {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        Preconditions.checkNotNull(vpnName, "vpnName cannot be null or empty!");
        Preconditions.checkNotNull(elanTag, "elanTag cannot be null or empty!");
        String nexthopIp = null;
        try {
            nexthopIp = InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, nhDpnId);
        } catch (Exception e) {
            LOG.warn("Unable to find nexthopip for subnetroute subnetip {}", subnetIp);
            return false;
        }
        if (nexthopIp != null) {
            VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getPrefixToInterfaceIdentifier(VpnUtil.getVpnId(dataBroker, vpnName), subnetIp),
                VpnUtil.getPrefixToInterface(nhDpnId, subnetId.getValue(), subnetIp, subnetId));
            vpnInterfaceManager.addSubnetRouteFibEntryToDS(rd, vpnName, subnetIp, nexthopIp, label, elanTag, nhDpnId,
                null);
            try {
                //BGP manager will handle withdraw and advertise internally if prefix
                //already exist
                bgpManager.advertisePrefix(rd, null /*macAddress*/, subnetIp, Collections.singletonList(nexthopIp),
                        VrfEntry.EncapType.Mplsgre, label, 0 /*l3vni*/, null /*gatewayMacAddress*/);
            } catch (Exception e) {
                LOG.error("Fail: Subnet route not advertised for rd {} subnetIp {}", rd, subnetIp, e);
                throw e;
            }
        } else {
            LOG.warn("The nexthopip is empty for subnetroute subnetip {}, ignoring fib route addition", subnetIp);
            return false;
        }
        return true;
    }

    private int getLabel(String rd, String subnetIp) {
        int label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
            VpnUtil.getNextHopLabelKey(rd, subnetIp));
        LOG.trace("Allocated subnetroute label {} for rd {} prefix {}", label, rd, subnetIp);
        return label;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void deleteSubnetRouteFromFib(String rd, String subnetIp, String vpnName) throws Exception {
        Preconditions.checkNotNull(rd, "RouteDistinguisher cannot be null or empty!");
        Preconditions.checkNotNull(subnetIp, "SubnetRouteIp cannot be null or empty!");
        vpnInterfaceManager.deleteSubnetRouteFibEntryFromDS(rd, subnetIp, vpnName);
        try {
            bgpManager.withdrawPrefix(rd, subnetIp);
        } catch (Exception e) {
            LOG.error("Fail: Subnet route not withdrawn for rd {} subnetIp {}", rd, subnetIp, e);
            throw e;
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void electNewDPNForSubNetRoute(SubnetOpDataEntryBuilder subOpBuilder, BigInteger dpnId, Uuid subnetId) {
        List<SubnetToDpn> subDpnList = null;
        boolean isRouteAdvertised = false;
        subDpnList = subOpBuilder.getSubnetToDpn();
        String rd = subOpBuilder.getVrfId();
        String subnetIp = subOpBuilder.getSubnetCidr();
        String vpnName = subOpBuilder.getVpnName();
        long elanTag = subOpBuilder.getElanTag();
        BigInteger nhDpnId = null;
        boolean isAlternateDpnSelected = false;
        Iterator<SubnetToDpn> subNetIt = subDpnList.iterator();
        int label = getLabel(rd, subnetIp);
        while (subNetIt.hasNext()) {
            SubnetToDpn subnetToDpn = subNetIt.next();
            nhDpnId = subnetToDpn.getDpnId();
            if (!nhDpnId.equals(dpnId)) {
                try {
                    //update the VRF entry for the subnetroute.
                    isRouteAdvertised = addSubnetRouteToFib(rd, subnetIp, nhDpnId, vpnName, elanTag, label, subnetId);
                    if (isRouteAdvertised) {
                        subOpBuilder.setRouteAdvState(TaskState.Done);
                        subOpBuilder.setNhDpnId(nhDpnId);
                        isAlternateDpnSelected = true;
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error(
                        "electNewDPNForSubNetRoute: Swapping and trying to configure NextHopDPN {} for subnet {} "
                            + "failed ex {}",
                        dpnId.toString(), subnetId.getValue(), ex);
                    subOpBuilder.setRouteAdvState(TaskState.Na);
                }
            }
        }

        //If no alternate Dpn is selected as nextHopDpn ,withdraw the subnetroute.
        if (!isAlternateDpnSelected) {
            LOG.info("No alternate DPN available for subnet {}.Prefix withdrawn from BGP", subnetIp);
            try {
                // Withdraw route from BGP for this subnet
                deleteSubnetRouteFromFib(rd, subnetIp, vpnName);
                subOpBuilder.setNhDpnId(null);
                subOpBuilder.setRouteAdvState(TaskState.Na);
            } catch (Exception ex) {
                LOG.error(
                    "electNewDPNForSubNetRoute: Withdrawing NextHopDPN " + dpnId.toString() + " information for subnet "
                        +
                        subnetId.getValue() + " from BGP failed {}" + ex);
                subOpBuilder.setRouteAdvState(TaskState.Pending);
            }
        }
    }
}

