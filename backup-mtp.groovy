#!/usr/bin/env groovy
println "MTP Backup";

def repoDesc = ".brepoid"
def repoBasePath = new File("/media/usb/mtpBackups/");
def mtpMount = new File("/media/mtp/");

def testMount = { File dir ->
	dir.directory && dir.canRead() && dir.listFiles().grep(~/[^.]+/);
}

if (!mtpMount.parentFile.list().contains(mtpMount.name)) {
	println "Cannot find mount point: ${mtpMount}";
	System.exit(1);
}

if (!repoBasePath.directory) {
	println "Cannot find repository base path: ${repoBasePath}";
	System.exit(1);
}

if (!testMount(mtpMount)) {
	println "Mount error. Remounting '${mtpMount}' ...";
	"umount ${mtpMount}".execute();
	"mount ${mtpMount}".execute();
	Thread.sleep(1000);
}

if (testMount(mtpMount)) {
	println "Mount point OK";
} else {
	println "Cannot mount '${mtpMount}'.";
	println mtpMount.listFiles();
	System.exit(1);
}

def loadRepos = { File dir ->
	def repos = new LinkedHashMap();
	for (File r: dir.listFiles()) {
		File rd = new File(r, repoDesc);
		if (rd.file && rd.canRead()) {
			String id = rd.text.trim();
			//println "Found repository '${r}' with id=${id}";
			repos[id]=r;
		}
	}
	return repos;
}

def repos = loadRepos(repoBasePath);
println "repos = ${repos}";

/* Recursive closure so def must be before assignment */
def findRoots;
findRoots = { File r ->
	if (!r.directory) {
		return [:]
	}
	File rd = new File(r, repoDesc);
	if (rd.file && rd.canRead()) {
		String id = rd.text.trim();
		println "Found root '${r}' with prev id=${id}";
		return [(id):r];
	} else {
		println "Trying root '${r}'";
		String id = "" + Math.abs(System.currentTimeMillis());
		//println "id '${id}'";
		try {
			rd << id;
			println "Found root '${r}' with new id=${id}";
			return [(id):r];
		} catch ( IOException e ) {
			println "Invalid root '${r}'";
		}
		def res = [:];
		r.listFiles().each{ it -> res << findRoots(it) };
		return res;
	}
}

def roots = findRoots(mtpMount);
println "mtp roots = ${roots}";

roots.each { k, v ->
	def repo = [(k):repos[k]];
	println "Repo for root ${k}:'${v}' = ${repo?repo:"Not found"}";
	if (repo == null) {
		repo = [(k):new File(repoBasePath, v.name.replaceAll("[^a-zA-Z_0-9]+","_")+"_"+k)];
		repo[k].mkdir();
		new File(repo[k], repoDesc) << v;
		repos << repo;
		println "New repo for root ${k}:'${v}' = ${repo[k]}. You are free to rename it later.";
	}
	println "Syncing '${v}'-->'${repo[k]}'...";
	def proc = ["rsync", "-Pavh", "--stats", "${v}/", "${repo[k]}/"].execute();
	proc.consumeProcessOutput(System.out, System.err);
	def result = proc.waitFor();
	println "Sync ${result == 0 ? "done" : "error ${result}"}";
}
