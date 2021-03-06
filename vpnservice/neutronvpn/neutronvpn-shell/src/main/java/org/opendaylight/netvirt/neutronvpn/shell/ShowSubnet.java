/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.shell;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker.syncReadOptional;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "subnet-show",
    description = "Comparison of data present in subnetMap and subnetOpDataEntry")
public class ShowSubnet extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ShowSubnet.class);

    @Option(name = "--subnetmap", aliases = {"--subnetmap"},
        description = "Display subnetMap details for given subnetId", required = false, multiValued = false)
    String subnetmap;

    @Option(name = "--subnetopdata", aliases = {"--subnetopdata"},
        description = "Display subnetOpData details for given subnetId", required = false, multiValued = false)
    String subnetopdata;

    private DataBroker dataBroker;
    private List<Subnetmap> subnetmapList = new ArrayList<>();
    private final Map<Uuid, SubnetOpDataEntry> subnetOpDataEntryMap = new HashMap<>();

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    protected Object doExecute() throws Exception {
        if (subnetmap == null && subnetopdata == null) {
            getSubnet();
            System.out.println("Following subnetId is present in both subnetMap and subnetOpDataEntry\n");
            for (Subnetmap subnetmap : subnetmapList) {
                SubnetOpDataEntry data = subnetOpDataEntryMap.get(subnetmap.getId());
                if (data != null) {
                    System.out.println(subnetmap.getId().toString() + "\n");
                }
            }
            System.out.println("\n\nFollowing subnetId is present in subnetMap but not in subnetOpDataEntry\n");
            for (Subnetmap subnetmap : subnetmapList) {
                SubnetOpDataEntry data = subnetOpDataEntryMap.get(subnetmap.getId());
                if (data == null) {
                    System.out.println(subnetmap.getId().toString() + "\n");
                }
            }
            getshowVpnCLIHelp();
        } else if (subnetmap == null && subnetopdata != null) {
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class)
                .child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(new Uuid(subnetopdata))).build();
            Optional<SubnetOpDataEntry> optionalSubs = syncReadOptional(dataBroker, OPERATIONAL, subOpIdentifier);
            SubnetOpDataEntry data = optionalSubs.get();
            System.out.println("Fetching subnetmap for given subnetId\n");
            System.out.println("------------------------------------------------------------------------------");
            System.out.println("Key: " + data.getKey() + "\n" + "VrfId: " + data.getVrfId() + "\n" + "ElanTag: "
                + "" + data.getElanTag() + "\n" + "NhDpnId: " + data.getNhDpnId() + "\n" + "RouteAdvState: "
                + data.getRouteAdvState() + "\n" + "SubnetCidr: " + data.getSubnetCidr() + "\n"
                + "SubnetToDpnList: " + data.getSubnetToDpn() + "\n" + "VpnName: " + data.getVpnName() + "\n");
            System.out.println("------------------------------------------------------------------------------");
        } else if (subnetmap != null && subnetopdata == null) {
            InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class)
                    .child(Subnetmap.class, new SubnetmapKey(new Uuid(subnetmap))).build();
            Optional<Subnetmap> sn = syncReadOptional(dataBroker, CONFIGURATION, id);
            Subnetmap data = sn.get();
            System.out.println("Fetching subnetopdataentry for given subnetId\n");
            System.out.println("------------------------------------------------------------------------------");
            System.out.println("Key: " + data.getKey() + "\n" + "VpnId: " + data.getVpnId() + "\n"
                    + "DirectPortList: " + data.getDirectPortList() + "\n" + "NetworkId: " + data.getNetworkId()
                    + "\n" + "Network-type: " + data.getNetworkType() + "\n" + "Network-segmentation-Id: "
                    + data.getSegmentationId() + "\n" + "PortList: " + data.getPortList() + "\n"
                    + "RouterInterfaceFixedIp: " + data.getRouterInterfaceFixedIp() + "\n"
                    + "RouterInterfacePortId: " + data.getRouterInterfacePortId().getValue() + "\n"
                    + "RouterIntfMacAddress: " + data.getRouterIntfMacAddress() + "\n" + "SubnetIp: "
                    + data.getSubnetIp() + "\n" + "TenantId: " + data.getTenantId() + "\n");
            System.out.println("------------------------------------------------------------------------------");
        }
        return null;
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void getSubnet() throws ReadFailedException {
        List<SubnetOpDataEntry> subnetOpDataEntryList = new ArrayList<>();
        InstanceIdentifier<Subnetmaps> subnetmapsid = InstanceIdentifier.builder(Subnetmaps.class).build();
        InstanceIdentifier<SubnetOpData> subOpIdentifier = InstanceIdentifier.builder(SubnetOpData.class).build();
        Optional<Subnetmaps> optionalSubnetmaps = syncReadOptional(dataBroker, CONFIGURATION, subnetmapsid);
        if (!optionalSubnetmaps.isPresent()) {
            System.out.println("No Subnetmaps configured.");
        } else {
            subnetmapList = optionalSubnetmaps.get().getSubnetmap();
        }

        Optional<SubnetOpData> optionalSubnetOpData = syncReadOptional(dataBroker, OPERATIONAL, subOpIdentifier);
        if (!optionalSubnetOpData.isPresent()) {
            System.out.println("No SubnetOpData configured.");
        } else {
            subnetOpDataEntryList = optionalSubnetOpData.get().getSubnetOpDataEntry();
        }

        for (SubnetOpDataEntry subnetOpDataEntry : subnetOpDataEntryList) {
            subnetOpDataEntryMap.put(subnetOpDataEntry.getSubnetId(), subnetOpDataEntry);
        }
    }

    // TODO Clean up the console output
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void getshowVpnCLIHelp() {
        System.out.println("\n Usage 1: "
            + "To display subnetMaps for a given subnetId subnet-show --subnetmap [<subnetId>]");
        System.out.println("\nUsage 2: "
            + "To display subnetOpDataEntry for a given subnetId subnet-show --subnetopdata [<subnetId>]");
    }
}
