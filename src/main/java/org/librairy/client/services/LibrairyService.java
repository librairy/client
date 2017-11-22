package org.librairy.client.services;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.librairy.boot.model.domain.resources.Domain;
import org.librairy.boot.model.domain.resources.Resource;
import org.librairy.boot.storage.dao.DomainsDao;
import org.librairy.boot.storage.dao.ItemsDao;
import org.librairy.boot.storage.generator.URIGenerator;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;
import org.librairy.client.models.LabeledLDALearn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyService {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyService.class);

    private final ItemsDao itemsDao;
    private final DomainsDao domainsDao;

    public LibrairyService(ApplicationContext context){
        this.itemsDao       = context.getBean(ItemsDao.class);
        this.domainsDao     = context.getBean(DomainsDao.class);
    }

    public String newItem(DataItem dataItem, List<String> domains) throws StorageError {

        if (dataItem.isEmpty()){
            throw new StorageError("DataItem without name or content");
        }

        String itemUri;
        try {
            itemUri = URIGenerator.fromId(Resource.Type.ITEM, URLEncoder.encode(dataItem.getName(),"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid item name: " + dataItem.getName(), e);
        }

        org.librairy.boot.model.domain.resources.Item internalItem = Resource.newItem(dataItem.getContent());
        internalItem.setDescription(dataItem.getName());
        internalItem.setLanguage(dataItem.getLanguage().name().toLowerCase());
        internalItem.setUri(itemUri);

        if (!itemsDao.save(internalItem)) throw new StorageError("DataItem not saved");


        if (!domains.isEmpty()){

            for(String domainId: domains){

                String domainUri = null;
                try {
                    domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, URLEncoder.encode(domainId,"UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new StorageError("Invalid domain name: " + domainId, e);
                }

                if (!domainsDao.exists(domainUri)){
                    Domain domain = Resource.newDomain(domainId);
                    domain.setUri(domainUri);
                    domainsDao.save(domain);
                }

                if (!domainsDao.addItem(domainUri, internalItem.getUri())) throw new StorageError("DataItem not added to domain: " + domainId);
            }
        }
        LOG.info("Saved: " + internalItem.getUri());
        return itemUri;
    }

    public void deleteItem(String itemName) throws StorageError {

        String itemUri;
        try {
            itemUri = URIGenerator.fromId(Resource.Type.ITEM, URLEncoder.encode(itemName,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid name: " + itemName, e);
        }

        if (!itemsDao.delete(itemUri)) throw new StorageError("DataItem not deleted");
        LOG.info("Deleted: " + itemUri);
    }

    public void deleteDomain(String domain) throws StorageError {
        String domainUri = null;
        try {
            domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, URLEncoder.encode(domain,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid domain name: " + domain, e);
        }
        domainsDao.delete(domainUri);
        LOG.info("Deleted: " + domainUri);
    }

    public void deleteAll(){
        itemsDao.deleteAll();
        LOG.info("Deleted All from librAIry");
    }


    public void newModel(DataModel.ALGORITHM algorithm, String domain, Integer iterations) throws ModelError {

        String domainId;
        try {
            domainId = URLEncoder.encode(domain,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ModelError("Invalid domain name: " + domain);
        }

        try {

            //create a temp file
            File csvFile = File.createTempFile("llda-"+domainId, ".csv");

            csvFile.mkdirs();
            csvFile.createNewFile();
            FileWriter writer = new FileWriter(csvFile);

            Map<Integer,String> labels = new HashMap<>();
            labels.put(0,"labelA labelB");
            labels.put(1,"labelB labelC");
            labels.put(2,"labelC labelD");
            labels.put(3,"labelA");

            final Escaper escaper = Escapers.builder()
                    .addEscape('\'',"")
                    .addEscape('\"',"")
                    .addEscape('('," ")
                    .addEscape(')'," ")
                    .addEscape('['," ")
                    .addEscape(']'," ")
                    .build();

            Map<Integer,String> contents = new HashMap<>();
            contents.put(0,"EDUCATION\n" +
                    "\n" +
                    "* Computer Systems Technology Program, Air Force Institute of Technology (AFIT), Graduate Courses in Software Engineering and Computer Communications (24 quarter units); GPA: 3.43\n" +
                    "\n" +
                    "* BS, Mathematics/Computer Science, University of California, Los Angeles (UCLA), GPA: 3.57; Major GPA: 3.62\n" +
                    "\n" +
                    "SPECIALIZED TRAINING\n" +
                    "\n" +
                    "* Database Administration, Performance Tuning, and Benchmarking with Oracle7; Oracle Corporation.\n" +
                    "\n" +
                    "* Software Requirements Engineering and Management Course; Computer Applications International Corporation.\n" +
                    "\n" +
                    "* X.400 Messaging and Allied Communications Procedures-123 Profile; ComTechnologies, Inc.\n" +
                    "\n" +
                    "* GOSIP LAN Operating System Network Administration; ETC, Inc.\n" +
                    "\n" +
                    "* Interactive UNIX System V r4 (POSIX) System Administration; ETC, Inc.\n" +
                    "\n" +
                    "* Effective Briefing Techniques and Technical Presentations; William French and Associates, Inc.\n" +
                    "\n" +
                    "* Transmission Control Protocol/Internet Protocol (TCP/IP); Technology Systems Institute.\n" +
                    "\n" +
                    "* LAN Interconnection Using Bridges, Routers, and Gateways; Information Systems Institute.\n" +
                    "\n" +
                    "* OSI X.400/X.500 Messaging and Directory Service Protocols; Communication Technologies, Inc.\n" +
                    "\n" +
                    "* US Army Signal Officer Advanced Course, US Army Signal Center, Georgia; Honor Graduate.\n" +
                    "\n" +
                    "CERTIFICATION, HONORS, & PROFESSIONAL AFFILIATIONS");
            contents.put(1,"Business Consultants, Inc., Washington, DC 1990-1993\n" +
                    "\n" +
                    "* Provided technical consulting services to the Smithsonian Institute’s Information Technology Services Group, Amnesty International, and internal research and development initiatives.\n" +
                    "\n" +
                    "* Consolidated and documented the Smithsonian Laboratory's Testing, Demonstration, and Training databases onto a single server, maximizing the use of the laboratory's computing resources.\n" +
                    "\n" +
                    "* Brought the Smithsonian Laboratory on-line with the Internet.\n" +
                    "\n" +
                    "* Successfully integrated and delivered to Amnesty International an $80,000 HP 9000/750 Server consisting of 8 Gigabytes of disk space and 9 software systems that required extensive porting work and documentation.");
            contents.put(2,"Designed and managed the development of an enterprise-level client/server automated auditing application for a major financial management company migrating from mainframe computers, db2, and FOCUS to a workgroup oriented, client/server architecture involving Windows for Workgroups, Windows NT Advanced Server, Microsoft SQL Server, Oracle7, and UNIX.\n" +
                    "\n" +
                    "* Designed an enterprise level, high performance, mission-critical, client/server database system incorporating symmetric multiprocessing computers (SMP), Oracle7’s Parallel Server, Tuxedo’s on-line transaction processing (OLTP) monitor, and redundant array of inexpensive disks (RAID) technology.\n" +
                    "\n" +
                    "* Conducted extensive trade studies of a large number of vendors that offer leading-edge technologies; these studies identified proven (low-risk) implementations of SMP and RDBMS systems that met stringent performance and availability criteria.\n" +
                    "\n");
            contents.put(3,"Computer Engineering Corporation, Los Angeles, CA, 1993-Present\n" +
                    "\n" +
                    "* Provide systems engineering, software engineering, technical consulting, and marketing services as a member of the Systems Integration Division of a software engineering consulting company.\n" +
                    "\n" +
                    "* Designed and managed the development of an enterprise-level client/server automated auditing application for a major financial management company migrating from mainframe computers, db2, and FOCUS to a workgroup oriented, client/server architecture involving Windows for Workgroups, Windows NT Advanced Server, Microsoft SQL Server, Oracle7, and UNIX.\n" +
                    "\n" +
                    "* Designed an enterprise level, high performance, mission-critical, client/server database system incorporating symmetric multiprocessing computers (SMP), Oracle7’s Parallel Server, Tuxedo’s on-line transaction processing (OLTP) monitor, and redundant array of inexpensive disks (RAID) technology.\n" +
                    "\n" +
                    "* Conducted extensive trade studies of a large number of vendors that offer leading-edge technologies; these studies identified proven (low-risk) implementations of SMP and RDBMS systems that met stringent performance and availability criteria.\n" +
                    "\n" +
                    "Systems Analyst\n" +
                    "\n" +
                    "Business Consultants, Inc., Washington, DC 1990-1993\n" +
                    "\n" +
                    "* Provided technical consulting services to the Smithsonian Institute’s Information Technology Services Group, Amnesty International, and internal research and development initiatives.\n" +
                    "\n" +
                    "* Consolidated and documented the Smithsonian Laboratory's Testing, Demonstration, and Training databases onto a single server, maximizing the use of the laboratory's computing resources.\n" +
                    "\n" +
                    "* Brought the Smithsonian Laboratory on-line with the Internet.\n" +
                    "\n" +
                    "* Successfully integrated and delivered to Amnesty International an $80,000 HP 9000/750 Server consisting of 8 Gigabytes of disk space and 9 software systems that required extensive porting work and documentation.\n" +
                    "\n" +
                    "Automated Data Processor\n" +
                    "\n" +
                    "US Army Infantry, Germany 1986-1990\n" +
                    "\n" +
                    "* Analyzed problems and ADP processes; designed, tested, and implemented software and hardware systems for an organizational operations center.\n" +
                    "\n" +
                    "* Supervised the maintenance, deployment, installation, and operation of a battalion's personnel system that monitored and controlled up to 12 platoons in a fast-paced, technically demanding environment.\n" +
                    "\n" +
                    "* Designed a maintenance reporting program that convert");

            for(int i=0;i<100;i++){
                String id       = "text" + i;
                String label    = labels.get(i%labels.size());
                String content  = contents.get(i%contents.size());
                writer.write(id+","+label+",\""+escaper.escape(content)+"\"\n");
            }

            writer.close();

            LOG.debug("created csv file: " + csvFile.getAbsolutePath());

            LabeledLDALearn.apply(csvFile.getAbsolutePath(), domainId ,iterations);
        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        }





    }


}
