package com.intel.mtwilson.agent.vmware;

import com.intel.mtwilson.model.Measurement;
import com.intel.mtwilson.model.Pcr;
import com.intel.mtwilson.model.PcrEventLog;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.PcrManifest;
import com.intel.mtwilson.model.Sha1Digest;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.HostTpmAttestationReport;
import com.vmware.vim25.HostTpmBootSecurityOptionEventDetails;
import com.vmware.vim25.HostTpmCommandEventDetails;
import com.vmware.vim25.HostTpmDigestInfo;
import com.vmware.vim25.HostTpmEventDetails;
import com.vmware.vim25.HostTpmEventLogEntry;
import com.vmware.vim25.HostTpmOptionEventDetails;
import com.vmware.vim25.HostTpmSoftwareComponentEventDetails;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.esapi.codecs.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VMWare51Esxi51   {
    private static Logger log = LoggerFactory.getLogger(VMWare51Esxi51.class);
    
    public static PcrManifest createPcrManifest(HostTpmAttestationReport report) {
        PcrManifest pcrManifest = new PcrManifest();
        
        // for each PCR get index and value
        for (HostTpmDigestInfo hostTpmDigestInfo : report.getTpmPcrValues()) {
            log.debug("HostTpmDigestInfo PCR {}", hostTpmDigestInfo.getPcrNumber());
            // this block just out of curiosity ... we should instantiate the right digest class using this info. expected to be SHA1... always...
            String algorithm = hostTpmDigestInfo.getDigestMethod();
            log.debug("HostTpmDigestInfo DigestMethod = {}", algorithm); // output is "SHA1"
            // convert the vwmare data types to mt wilson datatypes
            String digest = VMwareClient.byteArrayToHexString(hostTpmDigestInfo.getDigestValue());
            log.debug("HostTpmDigestInfo Digest = {}", digest); 
            Pcr pcr = new Pcr(hostTpmDigestInfo.getPcrNumber(), digest);
            pcrManifest.setPcr(pcr);
        }
        
        // for each event assign it to a PCR event log
        for(HostTpmEventLogEntry logEntry : report.getTpmEvents()) {
            int pcrIndex = logEntry.getPcrIndex();
            log.debug("PCR {}", pcrIndex);
            Measurement m = convertHostTpmEventLogEntryToMeasurement(logEntry);
            if( pcrManifest.containsPcrEventLog(PcrIndex.valueOf(pcrIndex)) ) {
                pcrManifest.getPcrEventLog(pcrIndex).getEventLog().add(m);
            }
            else {
                ArrayList<Measurement> list = new ArrayList<Measurement>();
                list.add(m);
                pcrManifest.setPcrEventLog(new PcrEventLog(PcrIndex.valueOf(pcrIndex),list));
            }
        }
        
        return pcrManifest;

	}
    
    private static Measurement convertHostTpmEventLogEntryToMeasurement(HostTpmEventLogEntry logEntry) {
        String label;
        String digest = VMwareClient.byteArrayToHexString(logEntry.getEventDetails().getDataHash());;
        HashMap<String,String> info = new HashMap<String,String>();
        
        HostTpmEventDetails logEventDetails = logEntry.getEventDetails();
        log.debug("Event {}", logEventDetails.getClass().getName());
				if (logEventDetails instanceof HostTpmSoftwareComponentEventDetails) { // ComponentName, VibName, VibVersion, VibVendor, and DataHash
					HostTpmSoftwareComponentEventDetails componentEventDetails = (HostTpmSoftwareComponentEventDetails)logEventDetails;
                    log.debug("Event name {}", componentEventDetails.getComponentName());
					info.put("EventType", "HostTpmSoftwareComponentEvent"); // new, properly capture the type of event in a separate field
					info.put("EventName", "Vim25Api.HostTpmSoftwareComponentEventDetails"); // XXX TODO the name should be the component name with package name and vendor-version... not the vmware event type, it's not unique
					info.put("ComponentName", "componentName." + componentEventDetails.getComponentName()); // XXX TODO remove the "componentName." prefix because we are capturing this now in EventType
					info.put("PackageName", componentEventDetails.getVibName());
					info.put("PackageVendor", componentEventDetails.getVibVendor());
					info.put("PackageVersion", componentEventDetails.getVibVersion());
//                    label = String.format("%s: %s-%s-%s", info.get("EventType"), componentEventDetails.getVibVendor(), componentEventDetails.getVibName(), componentEventDetails.getVibVersion() );
                    
                    // There are usually 3 components that are filenames like imgdb.tgz, state.tgz, and onetime.tgz, where the filename is listed as ComponentName and there is no PackageVendor, PackageName, or PackageVersion defined.
                    // So we need to label those using the ComponentName, and label the rest of the modules using PackageVendor-PackageName-PackageVersion.
                    if( (componentEventDetails.getVibVendor() == null || componentEventDetails.getVibVendor().isEmpty()) &&
                            (componentEventDetails.getVibName() == null || componentEventDetails.getVibName().isEmpty()) &&
                            (componentEventDetails.getVibVersion() == null || componentEventDetails.getVibVersion().isEmpty())) {
                        label = componentEventDetails.getComponentName(); // imgdb.tgz, state.tgz, onetime.tgz
                    }
                    else {
                        label = componentEventDetails.getComponentName(); // was doing vendor-package-version but now that attestation logic matches modules by digest instead of by name, the name doesn't matter. so we can use the short component name.
//                        label = String.format("%s-%s-%s", componentEventDetails.getVibVendor(), componentEventDetails.getVibName(), componentEventDetails.getVibVersion() ); // VMware-esx-xserver-5.1.0-0.0.799733, VMware-net-ixgbe-sriov-3.7.13.2iov-10vmw.510.0.0.613838, etc.
                    }
				} else if (logEventDetails instanceof HostTpmCommandEventDetails) { // CommandLine and DataHash
					HostTpmCommandEventDetails commandEventDetails = (HostTpmCommandEventDetails) logEventDetails;
                    log.debug("Event name {}", commandEventDetails.getCommandLine());

					info.put("EventType", "HostTpmCommandEvent"); // new, properly capture the type of event in a separate field
					info.put("EventName", "Vim25Api.HostTpmCommandEventDetails"); // XXX TODO the name should be the component name with package name and vendor-version... not the vmware event type, it's not unique
					info.put("ComponentName", "commandLine."+ getCommandLine(commandEventDetails)); // XXX TODO remove the "commandLine." prefix because we are capturing this now in EventType
                    info.put("UUID", getUuidFromCommandLine(commandEventDetails.getCommandLine()));
                    log.debug("UUID is {}", info.get("UUID"));
//                    label = String.format("%s: %s", info.get("EventType"), getCommandLine(commandEventDetails) );
                    label = String.format("%s", commandEventDetails.getCommandLine()); // UI should abbreviate it with "..." if desired...

				} else if (logEventDetails instanceof HostTpmOptionEventDetails) { // OptionsFilename, BootOptions, and DataHash
					HostTpmOptionEventDetails optionEventDetails = (HostTpmOptionEventDetails)logEventDetails;
                    log.debug("Event name {}", optionEventDetails.getOptionsFileName());

					info.put("EventType", "HostTpmOptionEvent"); // new, properly capture the type of event in a separate field
					info.put("EventName", "Vim25Api.HostTpmOptionEventDetails"); // XXX TODO the name should be the component name with package name and vendor-version... not the vmware event type, it's not unique
					info.put("ComponentName", "bootOptions."+ optionEventDetails.getOptionsFileName()); // XXX TODO remove the "bootOptions." prefix because we are capturing this now in EventType
                    // XXX TODO we can get the actual options with   info.put("BootOptions", VMwareClient.byteArrayToHexString(optionEventDetails.getBootOptions()); ... right now we are only capture the file name and not its contents;  probably ok since what the policy checks is the DIGEST anyway
//                    label = String.format("%s: %s", info.get("EventType"), optionEventDetails.getOptionsFileName() );
                    label = optionEventDetails.getOptionsFileName(); // String.format("%s", optionEventDetails.getOptionsFileName())
				} else if (logEventDetails instanceof HostTpmBootSecurityOptionEventDetails) { // BootSecurityOption and DataHash
					HostTpmBootSecurityOptionEventDetails optionEventDetails = (HostTpmBootSecurityOptionEventDetails)logEventDetails;
                    log.debug("Event name {}", optionEventDetails.getBootSecurityOption());
					info.put("EventType", "HostTpmBootSecurityOptionEvent"); // new, properly capture the type of event in a separate field
					info.put("EventName", "Vim25Api.HostTpmBootSecurityOptionEventDetails"); // XXX TODO the name should be the component name with package name and vendor-version... not the vmware event type, it's not unique
					info.put("ComponentName", "bootSecurityOption."+ optionEventDetails.getBootSecurityOption()); // XXX TODO remove the "bootSecurityOption." prefix because we are capturing this now in EventType
//                    label = String.format("%s: %s", info.get("EventType"), optionEventDetails.getBootSecurityOption() );
                    label = optionEventDetails.getBootSecurityOption();
				} else {
					log.warn("Unrecognized event in module event log "
							+ logEventDetails.getClass().getName());
                    
                    List<DynamicProperty> ps = logEventDetails.getDynamicProperty();
                    for(DynamicProperty p : ps) {
                        info.put(p.getName(), p.getVal().toString());
                    }
                    label = String.format("%s: %s", logEventDetails.getClass().getSimpleName(), logEventDetails.getDynamicType());
				}
                
                log.debug("Event Digest is {}", digest);
                // XXX for some reasons some measurements come in as 40 bytes (80 hex digits) instead of 20 bytes (40 hex digits)..  and those seem to be all zeros'
                if( digest.length() == 40 ) { // sha1 is 20 bytes, so 40 hex digits
                    return new Measurement(new Sha1Digest(digest), label, info );
                    
                }
                if( digest.replace("0", "").trim().isEmpty() ) {
                    log.warn("Event Digest is zero longer than 20 bytes: {}  -- replacing with 20 bytes of zero", digest);
                    return new Measurement(/*new Sha1Digest(digest)*/new Sha1Digest("0000000000000000000000000000000000000000"), label, info );
                }
                /**
                 * XXX the following lines may cause a problem.  If you are reading this, it's probably because
                 * you are troubleshooting a measurement that is misbehaving.  Need to find out why vmware is
                 * sending event logs with digests more than 20 bytes that are not zero... 
                 * You can't extend a PCR with something more than 20 bytes.  The TPM spec for PCR extend is PCRi = SHA1(PCRi||20-bytes-data).
                 * So the TSS automatically does a SHA1 hash on the data --  haven't read the source code so I don't know
                 * if it only hashes if it's longer than 20 bytes, or if it always hashes.  Probably always hashes for consistency.
                 */
                log.error("Event Digest is non-zero longer than 20 bytes: {}  -- replacing with SHA1 of value zero", digest);
                return new Measurement(Sha1Digest.valueOf(Hex.decode(digest)), label, info ); // XXX need some thought on how to handle this.  maybe change the Measurement class to accept ANY SIZE byte[]  instead of a Sha1Digest ??
                
    }

	private static String getCommandLine(HostTpmCommandEventDetails commandEventDetails) {
		String commandLine = commandEventDetails.getCommandLine();
        if (commandLine != null && commandLine.contains("no-auto-partition")){ // XXX this is the original logic... dont' know what value there is in deleting the information... a UI could always show it abbreviated with "..."
			commandLine = "";
		}
		return commandLine;
	}    
    
    // example input:    /b.b00 vmbTrustedBoot=true tboot=0x0x101a000 no-auto-partition bootUUID=772753050c0a140bdfbf92e306b9793d
    private static Pattern uuidPattern = Pattern.compile(".*bootUUID=([a-fA-F0-9]+).*");   // don't need [^a-fA-F0-9]?  before the last .* because the (a-fA-F0-9]+) match is greedy
    private static String getUuidFromCommandLine(String commandLine) {
        Matcher uuidMatcher = uuidPattern.matcher(commandLine);
        if( uuidMatcher.matches() ) {
            return uuidMatcher.group(1); 
        }
        return null;
    }
}