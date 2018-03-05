package org.point85.domain.persistence;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.point85.domain.collector.BaseEvent;
import org.point85.domain.collector.CollectorState;
import org.point85.domain.collector.DataCollector;
import org.point85.domain.collector.DataSource;
import org.point85.domain.collector.DataSourceType;
import org.point85.domain.collector.SetupHistory;
import org.point85.domain.opc.da.OpcDaSource;
import org.point85.domain.opc.ua.OpcUaSource;
import org.point85.domain.plant.Equipment;
import org.point85.domain.plant.EquipmentMaterial;
import org.point85.domain.plant.KeyedObject;
import org.point85.domain.plant.Material;
import org.point85.domain.plant.PlantEntity;
import org.point85.domain.plant.Reason;
import org.point85.domain.schedule.Rotation;
import org.point85.domain.schedule.Team;
import org.point85.domain.schedule.WorkSchedule;
import org.point85.domain.script.ScriptResolver;
import org.point85.domain.script.ScriptResolverType;
import org.point85.domain.uom.MeasurementSystem;
import org.point85.domain.uom.Unit;
import org.point85.domain.uom.UnitOfMeasure;
import org.point85.domain.uom.UnitOfMeasure.MeasurementType;
import org.point85.domain.uom.UnitType;

public class PersistencyService {
	// JPA persistence unit name
	private static final String PERSISTENCE_UNIT = "OEE";

	// entity manager factory
	private EntityManagerFactory emf;

	// singleton service
	private static PersistencyService persistencyService;

	private CompletableFuture<EntityManagerFactory> emfFuture;

	private Map<String, Boolean> namedQueryMap;

	private PersistencyService() {
		namedQueryMap = new ConcurrentHashMap<>();
	}

	public static PersistencyService instance() {
		if (persistencyService == null) {
			persistencyService = new PersistencyService();
		}
		return persistencyService;
	}

	public void initialize() {
		// create EM on a a background thread
		emfFuture = CompletableFuture.supplyAsync(() -> {
			//long before = System.currentTimeMillis();
			emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT);
			//System.out.println(("1.  msec to create EMF: " + (System.currentTimeMillis() - before)));
			return emf;
		});
	}

	public EntityManagerFactory getEntityManagerFactory() {
		if (emf == null && emfFuture != null) {
			try {
				emf = emfFuture.get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				emfFuture = null;
			}
		}
		return emf;
	}

	// get the EntityManager
	public EntityManager getEntityManager() {
		return getEntityManagerFactory().createEntityManager();
	}

	public List<String> fetchPlantEntityNames() {
		final String ENTITY_NAMES = "ENTITY.Names";

		if (namedQueryMap.get(ENTITY_NAMES) == null) {
			createNamedQuery(ENTITY_NAMES, "SELECT ent.name FROM PlantEntity ent");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(ENTITY_NAMES, String.class);
		return query.getResultList();
	}

	public PlantEntity fetchPlantEntityByName(String name) {
		final String ENTITY_BY_NAME = "ENTITY.Names";

		if (namedQueryMap.get(ENTITY_BY_NAME) == null) {
			createNamedQuery(ENTITY_BY_NAME, "SELECT ent FROM PlantEntity ent WHERE ent.name = :name");
		}

		PlantEntity entity = null;
		TypedQuery<PlantEntity> query = getEntityManager().createNamedQuery(ENTITY_BY_NAME, PlantEntity.class);
		List<PlantEntity> entities = query.getResultList();

		if (entities.size() == 1) {
			entity = entities.get(0);
		}
		return entity;
	}

	// fetch list of PersistentObjects by names
	public List<PlantEntity> fetchEntitiesByName(List<String> names) throws Exception {
		final String ENTITY_BY_NAME_LIST = "ENTITY.ByNameList";

		if (namedQueryMap.get(ENTITY_BY_NAME_LIST) == null) {
			createNamedQuery(ENTITY_BY_NAME_LIST, "SELECT ent FROM PlantEntity ent WHERE ent.name IN :names");
		}

		TypedQuery<PlantEntity> query = getEntityManager().createNamedQuery(ENTITY_BY_NAME_LIST, PlantEntity.class);
		query.setParameter("names", names);
		return query.getResultList();
	}

	public List<ScriptResolver> fetchScriptResolvers() {
		final String RESOLVER_ALL = "RESOLVER.All";

		if (namedQueryMap.get(RESOLVER_ALL) == null) {
			createNamedQuery(RESOLVER_ALL, "SELECT sr FROM ScriptResolver sr");
		}

		TypedQuery<ScriptResolver> query = getEntityManager().createNamedQuery(RESOLVER_ALL, ScriptResolver.class);
		return query.getResultList();
	}

	public List<String> fetchResolverSourceIds(String equipmentName, DataSourceType sourceType) {
		final String EQUIPMENT_SOURCE_IDS = "EQUIP.SourceIds";

		if (namedQueryMap.get(EQUIPMENT_SOURCE_IDS) == null) {
			createNamedQuery(EQUIPMENT_SOURCE_IDS,
					"SELECT sr.sourceId FROM ScriptResolver sr JOIN sr.equipment e JOIN sr.dataSource ds WHERE e.name = :name AND ds.sourceType = :type");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(EQUIPMENT_SOURCE_IDS, String.class);
		query.setParameter("name", equipmentName);
		query.setParameter("type", sourceType);
		return query.getResultList();
	}

	public List<DataSource> fetchDataSources(DataSourceType sourceType) {
		final String SRC_BY_TYPE = "DS.ByType";

		if (namedQueryMap.get(SRC_BY_TYPE) == null) {
			createNamedQuery(SRC_BY_TYPE, "SELECT source FROM DataSource source WHERE sourceType = :type");
		}

		TypedQuery<DataSource> query = getEntityManager().createNamedQuery(SRC_BY_TYPE, DataSource.class);
		query.setParameter("type", sourceType);
		return query.getResultList();
	}

	// remove the PersistentObject from the persistence context
	public void evict(KeyedObject object) {
		if (object == null) {
			return;
		}
		getEntityManager().detach(object);
	}

	// save the Persistent Object to the database
	public Object save(KeyedObject object) throws Exception {
		EntityManager em = getEntityManager();
		EntityTransaction txn = null;

		try {
			txn = em.getTransaction();
			txn.begin();

			// merge this entity into the PU and save
			Object merged = em.merge(object);

			// commit transaction
			txn.commit();

			return merged;
		} catch (Exception e) {
			// roll back transaction
			if (txn != null && txn.isActive()) {
				txn.rollback();
				e.printStackTrace();
			}
			throw new Exception(e.getMessage());
		} finally {
			em.close();
		}
	}

	// insert the object into the database
	public void persist(BaseEvent object) throws Exception {
		EntityManager em = getEntityManager();
		EntityTransaction txn = null;

		try {
			txn = em.getTransaction();
			txn.begin();

			// insert object
			em.persist(object);

			// commit transaction
			txn.commit();
		} catch (Exception e) {
			// roll back transaction
			if (txn != null && txn.isActive()) {
				txn.rollback();
				e.printStackTrace();
			}
			throw new Exception(e.getMessage());
		} finally {
			em.close();
		}
	}

	// delete the PersistentObjectfrom the database
	public void delete(KeyedObject keyed) throws Exception {
		if (keyed instanceof WorkSchedule) {
			// check for plant entity references
			List<PlantEntity> entities = fetchEntityCrossReferences((WorkSchedule) keyed);

			if (entities.size() > 0) {
				String refs = "";
				for (PlantEntity entity : entities) {
					if (refs.length() > 0) {
						refs += ", ";
					}
					refs += entity.getName();
				}
				throw new Exception("WorkSchedule " + ((WorkSchedule) keyed).getName()
						+ " is being referenced by plant entities " + refs);
			}
		}

		EntityManager em = getEntityManager();
		EntityTransaction txn = null;

		try {
			// start transaction
			txn = em.getTransaction();
			txn.begin();

			// delete
			Object po = em.find(keyed.getClass(), keyed.getKey());
			em.remove(po);

			// commit transaction
			txn.commit();
		} catch (Exception e) {
			// roll back transaction
			if (txn != null && txn.isActive()) {
				txn.rollback();
				e.printStackTrace();
			}
			throw new Exception(e.getMessage());
		} finally {
			em.close();
		}
	}

	// all entities
	public List<PlantEntity> fetchAllPlantEntities() {
		final String ENTITY_ALL = "ENTITY.All";

		if (namedQueryMap.get(ENTITY_ALL) == null) {
			createNamedQuery(ENTITY_ALL, "SELECT ent FROM PlantEntity ent");
		}

		TypedQuery<PlantEntity> query = getEntityManager().createNamedQuery(ENTITY_ALL, PlantEntity.class);
		return query.getResultList();
	}

	private void createNamedQuery(String name, String jsql) {
		Query query = getEntityManager().createQuery(jsql);
		getEntityManagerFactory().addNamedQuery(name, query);
		namedQueryMap.put(name, true);
	}

	// top-level plant entities
	public List<PlantEntity> fetchTopPlantEntities() {
		final String ENTITY_ROOTS = "ENTITY.Roots";

		if (namedQueryMap.get(ENTITY_ROOTS) == null) {
			createNamedQuery(ENTITY_ROOTS, "SELECT ent FROM PlantEntity ent WHERE ent.parent IS NULL");
		}

		TypedQuery<PlantEntity> query = getEntityManager().createNamedQuery(ENTITY_ROOTS, PlantEntity.class);
		return query.getResultList();
	}

	public List<DataCollector> fetchCollectorsByHostAndState(List<String> hostNames, List<CollectorState> states) {
		final String COLLECTOR_BY_HOST_BY_STATE = "COLLECT.ByStateByHost";

		if (namedQueryMap.get(COLLECTOR_BY_HOST_BY_STATE) == null) {
			createNamedQuery(COLLECTOR_BY_HOST_BY_STATE,
					"SELECT collector FROM DataCollector collector WHERE collector.host IN :names AND collector.state IN :states");
		}

		TypedQuery<DataCollector> query = getEntityManager().createNamedQuery(COLLECTOR_BY_HOST_BY_STATE,
				DataCollector.class);
		query.setParameter("names", hostNames);
		query.setParameter("states", states);
		return query.getResultList();
	}

	public List<DataCollector> fetchCollectorsByState(List<CollectorState> states) {
		final String COLLECTOR_BY_STATE = "COLLECT.ByState";

		if (namedQueryMap.get(COLLECTOR_BY_STATE) == null) {
			createNamedQuery(COLLECTOR_BY_STATE,
					"SELECT collector FROM DataCollector collector WHERE collector.state IN :states");
		}

		TypedQuery<DataCollector> query = getEntityManager().createNamedQuery(COLLECTOR_BY_STATE, DataCollector.class);
		query.setParameter("states", states);
		return query.getResultList();
	}

	public List<ScriptResolver> fetchScriptResolversByHost(List<String> hostNames, List<CollectorState> states)
			throws Exception {
		final String RESOLVER_BY_HOST = "RESOLVER.ByHost";

		if (namedQueryMap.get(RESOLVER_BY_HOST) == null) {
			createNamedQuery(RESOLVER_BY_HOST,
					"SELECT sr FROM ScriptResolver sr WHERE sr.collector.host IN :names AND sr.collector.state IN :states");
		}

		TypedQuery<ScriptResolver> query = getEntityManager().createNamedQuery(RESOLVER_BY_HOST, ScriptResolver.class);
		query.setParameter("names", hostNames);
		query.setParameter("states", states);
		return query.getResultList();
	}

	public List<ScriptResolver> fetchScriptResolversByCollector(List<String> definitionNames) throws Exception {
		final String RESOLVER_BY_COLLECTOR = "RESOLVER.ByCollector";

		if (namedQueryMap.get(RESOLVER_BY_COLLECTOR) == null) {
			createNamedQuery(RESOLVER_BY_COLLECTOR,
					"SELECT sr FROM ScriptResolver sr WHERE sr.collector.name IN :names");
		}

		TypedQuery<ScriptResolver> query = getEntityManager().createNamedQuery(RESOLVER_BY_COLLECTOR,
				ScriptResolver.class);
		query.setParameter("names", definitionNames);
		return query.getResultList();
	}

	public List<Material> fetchMaterialsByCategory(String category) throws Exception {
		final String MATLS_BY_CATEGORY = "MATL.ByCategory";

		if (namedQueryMap.get(MATLS_BY_CATEGORY) == null) {
			createNamedQuery(MATLS_BY_CATEGORY, "SELECT matl FROM Material matl WHERE matl.category = :category");
		}

		TypedQuery<Material> query = getEntityManager().createNamedQuery(MATLS_BY_CATEGORY, Material.class);
		query.setParameter("category", category);
		return query.getResultList();
	}

	public List<DataCollector> fetchAllDataCollectors() {
		final String COLLECT_ALL = "COLLECT.All";

		if (namedQueryMap.get(COLLECT_ALL) == null) {
			createNamedQuery(COLLECT_ALL, "SELECT collector FROM DataCollector collector");
		}

		TypedQuery<DataCollector> query = getEntityManager().createNamedQuery(COLLECT_ALL, DataCollector.class);
		return query.getResultList();
	}

	public List<Material> fetchAllMaterials() {
		final String MATL_ALL = "MATL.All";

		if (namedQueryMap.get(MATL_ALL) == null) {
			createNamedQuery(MATL_ALL, "SELECT matl FROM Material matl");
		}

		TypedQuery<Material> query = getEntityManager().createNamedQuery(MATL_ALL, Material.class);
		return query.getResultList();
	}

	public List<String> fetchMaterialCategories() {
		final String MATL_CATEGORIES = "MATL.Categories";

		if (namedQueryMap.get(MATL_CATEGORIES) == null) {
			createNamedQuery(MATL_CATEGORIES,
					"SELECT DISTINCT matl.category FROM Material matl WHERE matl.category IS NOT NULL");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(MATL_CATEGORIES, String.class);
		return query.getResultList();
	}

	public Material fetchMaterialByName(String materialName) {
		final String MATL_BY_NAME = "MATL.ByName";

		if (namedQueryMap.get(MATL_BY_NAME) == null) {
			createNamedQuery(MATL_BY_NAME, "SELECT matl FROM Material matl WHERE matl.name = :name");
		}

		Material material = null;
		TypedQuery<Material> query = getEntityManager().createNamedQuery(MATL_BY_NAME, Material.class);
		List<Material> materials = query.getResultList();

		if (materials.size() == 1) {
			material = materials.get(0);
		}
		return material;
	}

	public Material fetchMaterialByKey(Long key) throws Exception {
		return getEntityManager().find(Material.class, key);
	}

	public Reason fetchReasonByName(String name) {
		final String REASON_BY_NAME = "REASON.ByName";

		if (namedQueryMap.get(REASON_BY_NAME) == null) {
			createNamedQuery(REASON_BY_NAME, "SELECT reason FROM Reason reason WHERE reason.name = :name");
		}

		Reason reason = null;
		TypedQuery<Reason> query = getEntityManager().createNamedQuery(REASON_BY_NAME, Reason.class);
		List<Reason> reasons = query.getResultList();

		if (reasons.size() == 1) {
			reason = reasons.get(0);
		}
		return reason;
	}

	public Reason fetchReasonByKey(Long key) throws Exception {
		return getEntityManager().find(Reason.class, key);
	}

	public List<Reason> fetchAllReasons() {
		final String REASON_ALL = "REASON.All";

		if (namedQueryMap.get(REASON_ALL) == null) {
			createNamedQuery(REASON_ALL, "SELECT reason FROM Reason reason");
		}

		TypedQuery<Reason> query = getEntityManager().createNamedQuery(REASON_ALL, Reason.class);
		return query.getResultList();
	}

	// top-level reasons
	public List<Reason> fetchTopReasons() {
		final String REASON_ROOTS = "REASON.Roots";

		if (namedQueryMap.get(REASON_ROOTS) == null) {
			createNamedQuery(REASON_ROOTS, "SELECT reason FROM Reason reason WHERE reason.parent IS NULL");
		}

		TypedQuery<Reason> query = getEntityManager().createNamedQuery(REASON_ROOTS, Reason.class);
		return query.getResultList();
	}

	public List<String> fetchProgIds() {
		final String DA_PROG_IDS = "OPCDA.ProgIds";

		if (namedQueryMap.get(DA_PROG_IDS) == null) {
			createNamedQuery(DA_PROG_IDS, "SELECT source.name FROM OpcDaSource source");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(DA_PROG_IDS, String.class);
		return query.getResultList();
	}

	public OpcDaSource fetchOpcDaSourceByName(String name) {
		final String DA_SRC_BY_NAME = "OPCDA.ByName";

		if (namedQueryMap.get(DA_SRC_BY_NAME) == null) {
			createNamedQuery(DA_SRC_BY_NAME, "SELECT source FROM OpcDaSource source WHERE source.name = :name");
		}

		OpcDaSource source = null;
		TypedQuery<OpcDaSource> query = getEntityManager().createNamedQuery(DA_SRC_BY_NAME, OpcDaSource.class);
		query.setParameter("name", name);

		List<OpcDaSource> sources = query.getResultList();

		if (sources.size() == 1) {
			source = sources.get(0);
		}
		return source;
	}

	public OpcUaSource fetchOpcUaSourceByName(String name) {
		final String UA_SRC_BY_NAME = "OPCUA.ByName";

		if (namedQueryMap.get(UA_SRC_BY_NAME) == null) {
			createNamedQuery(UA_SRC_BY_NAME, "SELECT source FROM OpcUaSource source WHERE source.name = :name");
		}

		OpcUaSource source = null;
		TypedQuery<OpcUaSource> query = getEntityManager().createNamedQuery(UA_SRC_BY_NAME, OpcUaSource.class);
		query.setParameter("name", name);

		List<OpcUaSource> sources = query.getResultList();

		if (sources.size() == 1) {
			source = sources.get(0);
		}
		return source;
	}

	// ******************** work schedule related *******************************
	public WorkSchedule fetchScheduleByKey(Long key) throws Exception {
		return getEntityManager().find(WorkSchedule.class, key);
	}

	public List<WorkSchedule> fetchWorkSchedules() {
		final String WS_SCHEDULES = "WS.Schedules";

		if (namedQueryMap.get(WS_SCHEDULES) == null) {
			createNamedQuery(WS_SCHEDULES, "SELECT ws FROM WorkSchedule ws");
		}

		TypedQuery<WorkSchedule> query = getEntityManager().createNamedQuery(WS_SCHEDULES, WorkSchedule.class);
		return query.getResultList();
	}

	public List<String> fetchWorkScheduleNames() {
		final String WS_NAMES = "WS.Names";

		if (namedQueryMap.get(WS_NAMES) == null) {
			createNamedQuery(WS_NAMES, "SELECT ws.name FROM WorkSchedule ws");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(WS_NAMES, String.class);
		return query.getResultList();
	}

	// fetch Team by its primary key
	public Team fetchTeamByKey(Long key) throws Exception {
		return getEntityManager().find(Team.class, key);
	}

	public OffsetDateTime fetchDatabaseTime() {
		String mssqlQuery = "select convert(nvarchar(100), SYSDATETIMEOFFSET(), 126)";
		Query query = getEntityManager().createNativeQuery(mssqlQuery);
		String result = (String) query.getSingleResult();
		OffsetDateTime time = OffsetDateTime.parse(result, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		return time;
	}

	// get any Team references to the Rotation
	public List<Team> fetchTeamCrossReferences(Rotation rotation) throws Exception {
		final String WS_ROT_XREF = "WS.ROT.CrossRef";

		if (namedQueryMap.get(WS_ROT_XREF) == null) {
			createNamedQuery(WS_ROT_XREF, "SELECT team FROM Team team WHERE rotation = :rotation");
		}

		TypedQuery<Team> query = getEntityManager().createNamedQuery(WS_ROT_XREF, Team.class);
		query.setParameter("rotation", rotation);
		return query.getResultList();
	}

	public List<PlantEntity> fetchEntityCrossReferences(WorkSchedule schedule) {
		final String WS_ENT_XREF = "WS.ENT.CrossRef";

		if (namedQueryMap.get(WS_ENT_XREF) == null) {
			createNamedQuery(WS_ENT_XREF, "SELECT ent FROM PlantEntity ent WHERE ent.workSchedule = :schedule");
		}

		TypedQuery<PlantEntity> query = getEntityManager().createNamedQuery(WS_ENT_XREF, PlantEntity.class);
		query.setParameter("schedule", schedule);
		return query.getResultList();
	}

	// ******************** unit of measure related ***************************
	public UnitOfMeasure fetchUomByKey(Long key) throws Exception {
		return getEntityManager().find(UnitOfMeasure.class, key);
	}

	// get symbols and names in this category
	public List<String[]> fetchUomSymbolsAndNamesByCategory(String category) {
		final String UOM_CAT_SYMBOLS = "UOM.SymbolsInCategory";

		if (namedQueryMap.get(UOM_CAT_SYMBOLS) == null) {
			createNamedQuery(UOM_CAT_SYMBOLS,
					"SELECT uom.symbol, uom.name FROM UnitOfMeasure uom WHERE uom.category = :category");
		}

		TypedQuery<String[]> query = getEntityManager().createNamedQuery(UOM_CAT_SYMBOLS, String[].class);
		query.setParameter("category", category);
		return query.getResultList();
	}

	// fetch symbols and their names for this UOM type
	public List<String[]> fetchUomSymbolsAndNamesByType(UnitType unitType) {
		final String UOM_SYMBOLS = "UOM.Symbols";

		if (namedQueryMap.get(UOM_SYMBOLS) == null) {
			createNamedQuery(UOM_SYMBOLS,
					"SELECT uom.symbol, uom.name FROM UnitOfMeasure uom WHERE uom.unit IS NULL AND uom.unitType = :type");
		}

		TypedQuery<String[]> query = getEntityManager().createNamedQuery(UOM_SYMBOLS, String[].class);
		query.setParameter("type", unitType);
		return query.getResultList();
	}

	// fetch all defined categories
	public List<String> fetchCategories() {
		final String UOM_CATEGORIES = "UOM.Categories";

		if (namedQueryMap.get(UOM_CATEGORIES) == null) {
			createNamedQuery(UOM_CATEGORIES, "SELECT DISTINCT uom.category FROM UnitOfMeasure uom");
		}

		TypedQuery<String> query = getEntityManager().createNamedQuery(UOM_CATEGORIES, String.class);
		return query.getResultList();
	}

	// query for UOM based on its unique symbol
	public UnitOfMeasure fetchUOMBySymbol(String symbol) throws Exception {
		final String UOM_BY_SYMBOL = "UOM.BySymbol";

		if (namedQueryMap.get(UOM_BY_SYMBOL) == null) {
			createNamedQuery(UOM_BY_SYMBOL, "SELECT uom FROM UnitOfMeasure uom WHERE uom.symbol = :symbol");
		}

		TypedQuery<UnitOfMeasure> query = getEntityManager().createNamedQuery(UOM_BY_SYMBOL, UnitOfMeasure.class);
		query.setParameter("symbol", symbol);
		return query.getSingleResult();
	}

	public List<UnitOfMeasure> fetchUomsByCategory(String category) throws Exception {
		final String UOM_BY_CATEGORY = "UOM.ByCategory";

		if (namedQueryMap.get(UOM_BY_CATEGORY) == null) {
			createNamedQuery(UOM_BY_CATEGORY, "SELECT uom FROM UnitOfMeasure uom WHERE uom.category = :category");
		}

		TypedQuery<UnitOfMeasure> query = getEntityManager().createNamedQuery(UOM_BY_CATEGORY, UnitOfMeasure.class);
		query.setParameter("category", category);
		return query.getResultList();
	}

	// fetch UOM by its enumeration
	public UnitOfMeasure fetchUOMByUnit(Unit unit) throws Exception {
		final String UOM_BY_UNIT = "UOM.ByUnit";

		if (namedQueryMap.get(UOM_BY_UNIT) == null) {
			createNamedQuery(UOM_BY_UNIT, "SELECT uom FROM UnitOfMeasure uom WHERE uom.unit = :unit");
		}

		UnitOfMeasure uom = null;

		// fetch by Unit enum
		TypedQuery<UnitOfMeasure> query = getEntityManager().createNamedQuery(UOM_BY_UNIT, UnitOfMeasure.class);
		query.setParameter("unit", unit);

		List<UnitOfMeasure> uoms = query.getResultList();

		if (uoms.size() == 1) {
			uom = uoms.get(0);
		} else {
			// not in db, get from pre-defined units
			uom = MeasurementSystem.instance().getUOM(unit);
		}

		// fetch units that it references
		fetchReferencedUnits(uom);

		return uom;
	}

	// fetch recursively all referenced units to make them managed in the
	// persistence unit
	public void fetchReferencedUnits(UnitOfMeasure uom) throws Exception {
		String id = null;
		UnitOfMeasure referenced = null;
		UnitOfMeasure fetched = null;

		// abscissa unit
		referenced = uom.getAbscissaUnit();
		if (referenced != null && !uom.isTerminal()) {
			id = referenced.getSymbol();
			fetched = fetchUOMBySymbol(id);

			if (fetched != null) {
				// already in database
				uom.setAbscissaUnit(fetched);
			}

			// units referenced by the abscissa
			fetchReferencedUnits(referenced);
		}

		// bridge abscissa unit
		referenced = uom.getBridgeAbscissaUnit();
		if (referenced != null) {
			id = referenced.getSymbol();
			fetched = fetchUOMBySymbol(id);

			if (fetched != null) {
				// already in database
				uom.setBridgeConversion(uom.getBridgeScalingFactor(), fetched, uom.getBridgeOffset());
			}
		}

		// UOM1 and UOM2
		if (uom.getMeasurementType().equals(MeasurementType.PRODUCT)) {
			// multiplier
			UnitOfMeasure uom1 = uom.getMultiplier();
			id = uom1.getSymbol();
			fetched = fetchUOMBySymbol(id);

			if (fetched != null) {
				uom1 = fetched;
			}

			// multiplicand
			UnitOfMeasure uom2 = uom.getMultiplicand();
			id = uom2.getSymbol();
			UnitOfMeasure fetched2 = fetchUOMBySymbol(id);

			if (fetched2 != null) {
				uom2 = fetched2;
			}

			uom.setProductUnits(uom1, uom2);

			// units referenced by UOM1 & 2
			fetchReferencedUnits(uom1);
			fetchReferencedUnits(uom2);

		} else if (uom.getMeasurementType().equals(MeasurementType.QUOTIENT)) {
			// dividend
			UnitOfMeasure uom1 = uom.getDividend();
			id = uom1.getSymbol();
			fetched = fetchUOMBySymbol(id);

			if (fetched != null) {
				uom1 = fetched;
			}

			// divisor
			UnitOfMeasure uom2 = uom.getDivisor();
			id = uom2.getSymbol();
			UnitOfMeasure fetched2 = fetchUOMBySymbol(id);

			if (fetched2 != null) {
				uom2 = fetched2;
			}

			uom.setQuotientUnits(uom1, uom2);

			// units referenced by UOM1 & 2
			fetchReferencedUnits(uom1);
			fetchReferencedUnits(uom2);

		} else if (uom.getMeasurementType().equals(MeasurementType.POWER)) {
			referenced = uom.getPowerBase();
			id = referenced.getSymbol();
			fetched = fetchUOMBySymbol(id);

			if (fetched != null) {
				// already in database
				uom.setPowerUnit(fetched, uom.getPowerExponent());
			}

			// units referenced by the power base
			fetchReferencedUnits(referenced);
		}
	}

	public SetupHistory fetchLastHistory(Equipment equipment, ScriptResolverType type) {
		final String LAST_RECORD = "Setup.Last";

		if (namedQueryMap.get(LAST_RECORD) == null) {
			createNamedQuery(LAST_RECORD,
					"SELECT hist FROM SetupHistory hist WHERE hist.equipment = :equipment AND hist.type = :type ORDER BY hist.sourceTimestamp DESC");
		}

		TypedQuery<SetupHistory> query = getEntityManager().createNamedQuery(LAST_RECORD, SetupHistory.class);
		query.setParameter("equipment", equipment);
		query.setParameter("type", type);
		query.setMaxResults(1);
		List<SetupHistory> histories = query.getResultList();

		SetupHistory history = null;
		if (histories.size() == 1) {
			history = histories.get(0);
		}

		return history;
	}

	public List<EquipmentMaterial> fetchEquipmentMaterials(UnitOfMeasure uom) throws Exception {
		final String EQM_XREF = "EQM.XRef";

		if (namedQueryMap.get(EQM_XREF) == null) {
			createNamedQuery(EQM_XREF,
					"SELECT eqm FROM EquipmentMaterial eqm WHERE runRateUOM = :uom OR rejectUOM = :uom");
		}

		TypedQuery<EquipmentMaterial> query = getEntityManager().createNamedQuery(EQM_XREF, EquipmentMaterial.class);
		query.setParameter("uom", uom);
		return query.getResultList();
	}

	public List<UnitOfMeasure> fetchUomCrossReferences(UnitOfMeasure uom) throws Exception {
		final String UOM_XREF = "UOM.CrossRef";

		if (namedQueryMap.get(UOM_XREF) == null) {
			createNamedQuery(UOM_XREF,
					"SELECT uom FROM UnitOfMeasure uom WHERE uom1 = :uom OR uom2 = :uom OR abscissaUnit = :uom OR bridgeAbscissaUnit = :uom");
		}

		TypedQuery<UnitOfMeasure> query = getEntityManager().createNamedQuery(UOM_XREF, UnitOfMeasure.class);
		query.setParameter("uom", uom);
		return query.getResultList();
	}

	public DataCollector fetchCollectorByName(String name) {
		final String COLLECT_BY_NAME = "COLLECT.ByName";

		if (namedQueryMap.get(COLLECT_BY_NAME) == null) {
			createNamedQuery(COLLECT_BY_NAME,
					"SELECT collector FROM DataCollector collector WHERE collector.name = :name");
		}

		DataCollector collector = null;
		TypedQuery<DataCollector> query = getEntityManager().createNamedQuery(COLLECT_BY_NAME, DataCollector.class);
		List<DataCollector> collectors = query.getResultList();

		if (collectors.size() == 1) {
			collector = collectors.get(0);
		}
		return collector;
	}

}
