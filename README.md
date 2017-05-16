# User Defined Adapter Tempalte for AsterixDB

AsterixDB supports user to drop their own adapters to ingest from the datasources 
using the feed mechanism. This is a template which shows how to write your 
own adapters for AsterixDB.

### How to use
* Download a AsterixDB instance from [here](asterixdb.apache.org).
* Clone this repo and execute the `pack.sh`.
* Drop the compiled jar file into `asterix-server-0.9.1-SNAPSHOT-binary-assembly/repo/` (Depending on
your AsterixDB version, the version number may vary).
* (If you are using the managix installer, you need to repack the server package and replace the old 
one; If you are using NCServices, simply dropping the jar to the specified directory is enough).
* Here is a sample AQL that creates a feed with CAP adapter:
```
drop dataverse document_feeds if exists;
create dataverse document_feeds;
use dataverse document_feeds;

create type CAP_Alert as open{
 identifier: string
}

create dataset CAP_Alerts(CAP_Alert)
primary key identifier;

create feed CAP_Feed using socket_adapter
(
	("sockets"="127.0.0.1:10001"),
	("address-type"="IP"),
	("type-name"="CAP_Alert"),
	("format"="cap")
);

connect feed CAP_Feed to dataset CAP_Alerts;
start feed CAP_Feed;
```

### Write your own adapter
You can easily adapt the given codes for CAP Adapter to your own. Here are several points that are useful 
for you to understand the adatper mechanism in AsterixDB.
* A feed consists of two parts: Adapter(Reader) and Parser. The adapter takes bytes for datasources without knowing 
the content but only the record boundary, which is to say the adapter guaranntees the parser will get a complete 
record. The parser receives records passed from adaptor and process it into an AsterixDB friendly format (ADM) for
further processing.
* If you are writting a stream-based adaptor, please implement the StreamRecordReader interface and add your implementation
in `org.apache.asterix.external.input.record.reader.stream.StreamRecordReader`. If you are implementing general IRecordReader 
interface, add your implementation in `org.apache.asterix.external.api.IRecordReaderFactory`. You may find more examples in the 
AsterixDB code base.
* When constructing your own adapter and parser, make sure the `recordReaderFormats` and `parserFormats` are consistent and both
of them are public. 
