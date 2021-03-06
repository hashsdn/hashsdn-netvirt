/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpManager {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpManager.class);
    private final IMdsalApiManager mdsalUtil;
    private final INeutronVpnManager neutronVpnService;
    private final DhcpserviceConfig config;
    private final DataBroker broker;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;

    private int dhcpOptLeaseTime = 0;
    private String dhcpOptDefDomainName;
    private DhcpInterfaceEventListener dhcpInterfaceEventListener;
    private DhcpInterfaceConfigListener dhcpInterfaceConfigListener;

    @Inject
    public DhcpManager(final IMdsalApiManager mdsalApiManager,
            final INeutronVpnManager neutronVpnManager,
            final DhcpserviceConfig config, final DataBroker dataBroker,
            final DhcpExternalTunnelManager dhcpExternalTunnelManager, final IInterfaceManager interfaceManager,
            final @Named("elanService") IElanService ielanService) {
        this.mdsalUtil = mdsalApiManager;
        this.neutronVpnService = neutronVpnManager;
        this.config = config;
        this.broker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.interfaceManager = interfaceManager;
        this.elanService = ielanService;
        configureLeaseDuration(DhcpMConstants.DEFAULT_LEASE_TIME);
    }

    @PostConstruct
    public void init() {
        LOG.trace("Netvirt DHCP Manager Init .... {}",config.isControllerDhcpEnabled());
        if (config.isControllerDhcpEnabled()) {
            dhcpInterfaceEventListener = new DhcpInterfaceEventListener(this, broker, dhcpExternalTunnelManager,
                    interfaceManager, elanService);
            dhcpInterfaceConfigListener = new DhcpInterfaceConfigListener(broker, dhcpExternalTunnelManager, this);
            LOG.info("DHCP Service initialized");
        }
    }

    @PreDestroy
    public void close() throws Exception {
        if (dhcpInterfaceEventListener != null) {
            dhcpInterfaceEventListener.close();
        }
        if (dhcpInterfaceConfigListener != null) {
            dhcpInterfaceConfigListener.close();
        }
        LOG.info("DHCP Service closed");
    }

    public int setLeaseDuration(int leaseDuration) {
        configureLeaseDuration(leaseDuration);
        return getDhcpLeaseTime();
    }

    public String setDefaultDomain(String defaultDomain) {
        this.dhcpOptDefDomainName = defaultDomain;
        return getDhcpDefDomain();
    }

    protected int getDhcpLeaseTime() {
        return this.dhcpOptLeaseTime;
    }

    protected int getDhcpRenewalTime() {
        return this.dhcpOptLeaseTime;
    }

    protected int getDhcpRebindingTime() {
        return this.dhcpOptLeaseTime;
    }

    protected String getDhcpDefDomain() {
        return this.dhcpOptDefDomainName;
    }

    private void configureLeaseDuration(int leaseTime) {
        this.dhcpOptLeaseTime = leaseTime;
    }

    public Subnet getNeutronSubnet(Port port) {
        if (port != null) {
            // DHCP Service is only interested in IPv4 IPs/Subnets
            for (FixedIps fixedIp: port.getFixedIps()) {
                if (fixedIp.getIpAddress().getIpv4Address() != null) {
                    return neutronVpnService.getNeutronSubnet(fixedIp.getSubnetId());
                }
            }
        }
        return null;
    }

    public Port getNeutronPort(String name) {
        try {
            return neutronVpnService.getNeutronPort(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void installDhcpEntries(BigInteger dpnId, String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.ADD_FLOW,
                mdsalUtil, tx);
    }

    public void unInstallDhcpEntries(BigInteger dpId, String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.DEL_FLOW,
                mdsalUtil, tx);
    }

    public void setupDefaultDhcpFlows(BigInteger dpId) {
        setupTableMissForDhcpTable(dpId);
        if (config.isDhcpDynamicAllocationPoolEnabled()) {
            setupDhcpAllocationPoolFlow(dpId);
        }
    }

    private void setupTableMissForDhcpTable(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE, "DHCPTableMissFlow",
                0, "DHCP Table Miss Flow", 0, 0,
                DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        DhcpServiceCounters.install_dhcp_table_miss_flow.inc();
        mdsalUtil.installFlow(flowEntity);
        setupTableMissForHandlingExternalTunnel(dpId);
    }

    private void setupDhcpAllocationPoolFlow(BigInteger dpId) {
        List<MatchInfo> matches = DhcpServiceUtils.getDhcpMatch();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE,
                "DhcpAllocationPoolFlow", DhcpMConstants.DEFAULT_DHCP_ALLOCATION_POOL_FLOW_PRIORITY,
                "Dhcp Allocation Pool Flow", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        LOG.trace("Installing DHCP Allocation Pool Flow DpId {}", dpId);
        DhcpServiceCounters.install_dhcp_flow.inc();
        mdsalUtil.installFlow(flowEntity);
    }

    private void setupTableMissForHandlingExternalTunnel(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.EXTERNAL_TUNNEL_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL,
                "DHCPTableMissFlowForExternalTunnel",
                0, "DHCP Table Miss Flow For External Tunnel", 0, 0,
                DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        DhcpServiceCounters.install_dhcp_table_miss_flow_for_external_table.inc();
        mdsalUtil.installFlow(flowEntity);
    }
}
