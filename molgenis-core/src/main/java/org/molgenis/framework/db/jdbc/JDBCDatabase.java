package org.molgenis.framework.db.jdbc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.molgenis.MolgenisOptions;
import org.molgenis.framework.db.AbstractDatabase;
import org.molgenis.framework.db.DatabaseException;
import org.molgenis.framework.db.ExampleData;

/**
 * JDBC implementation of Database to query relational databases.
 * <p>
 * In order to function, {@link org.molgenis.framework.db.jdbc.JDBCMapper} must
 * be added for each {@link org.molgenis.util.Entity} E that can be queried.
 * These mappers take care of the conversion of Java Entity to the relational
 * database tables using the SQL specific to that source. Typically, these
 * mappers are generated by subclassing JDBCDatabase:
 * 
 * <pre>
 * public JDBCDatabase(DataSource data_src, File file_src) throws DatabaseException
 * {
 * 	super(data_src, file_src);
 * 	this.putMapper(Experiment.class, new ExperimentMapper());
 * 	this.putMapper(Assay.class, new AssayMapper());
 * 	this.putMapper(Data.class, new DataMapper());
 * 	this.putMapper(Protocol.class, new ProtocolMapper());
 * 	this.putMapper(Item.class, new ItemMapper());
 * 	this.putMapper(Subject.class, new SubjectMapper());
 * }
 * </pre>
 */
public class JDBCDatabase extends AbstractDatabase
{
	/** Logger for this database */
	private static final Logger logger = Logger.getLogger(JDBCDatabase.class);

	/**
	 * Construct a JDBCDatabase using this connection alone. There is no
	 * DataSource, which is used for checks in getConnection and
	 * closeConnection. Used by the FrontController which manages the database
	 * connections itself.
	 * 
	 * @param conn
	 */
	public JDBCDatabase(Connection conn)
	{
		this.connection = conn;
	}

	/**
	 * Construct a JDBCDatabase to query relational database.
	 * 
	 * @param data_src
	 *            JDBC DataSource that contains the persistent data.
	 * @param file_source
	 *            File directory where file attachements can be stored.
	 * @throws DatabaseException
	 */
	public JDBCDatabase(DataSource data_src, File file_source) throws DatabaseException
	{
		this(new SimpleDataSourceWrapper(data_src));

		// optional: requires a fileSource
		if (file_source == null) logger.warn("JDBCDatabase: fileSource is missing");
		this.fileSource = file_source;
	}

	public JDBCDatabase(DataSourceWrapper data_src, File file_source)
	{
		this(data_src);

		// optional: requires a fileSource
		if (file_source == null) logger.warn("JDBCDatabase: fileSource is missing");
		this.fileSource = file_source;
	}

	public JDBCDatabase(MolgenisOptions options)
	{
		this.options = options;

		BasicDataSource dSource = new BasicDataSource();
		dSource.setDriverClassName(options.db_driver);
		dSource.setUsername(options.db_user);
		dSource.setPassword(options.db_password);
		dSource.setUrl(options.db_uri);

		this.source = new SimpleDataSourceWrapper(dSource);

		File file_source = new File(options.db_filepath);
		this.fileSource = file_source;

		logger.debug("JDBCDatabase(uri=" + options.db_uri + ") created");
	}

	public JDBCDatabase(Properties p)
	{
		this(new MolgenisOptions(p));
	}

	public JDBCDatabase(File propertiesFilePath) throws FileNotFoundException, IOException
	{
		super();
		Properties p = new Properties();
		InputStream is = new FileInputStream(propertiesFilePath);
		try
		{
			p.load(is);
		}
		finally
		{
			IOUtils.closeQuietly(is);
		}
		BasicDataSource dSource = new BasicDataSource();
		dSource.setDriverClassName(p.getProperty("db_driver"));
		dSource.setUsername(p.getProperty("db_user"));
		dSource.setPassword(p.getProperty("db_password"));
		dSource.setUrl(p.getProperty("db_uri"));

		this.source = new SimpleDataSourceWrapper(dSource);

		File file_source = new File(p.getProperty("db_filepath"));
		this.fileSource = file_source;

	}

	public JDBCDatabase(String propertiesFilePath) throws FileNotFoundException, IOException
	{
		super();
		Properties p = new Properties();
		InputStream is = null;
		try
		{
			is = new FileInputStream(propertiesFilePath);
			p.load(is);
		}
		catch (IOException e)
		{
			InputStream is2 = ClassLoader.getSystemResourceAsStream(propertiesFilePath);
			try
			{
				p.load(is2);
			}
			finally
			{
				IOUtils.closeQuietly(is2);
			}
		}
		finally
		{
			IOUtils.closeQuietly(is);
		}

		BasicDataSource dSource = new BasicDataSource();
		dSource.setDriverClassName(p.getProperty("db_driver"));
		dSource.setUsername(p.getProperty("db_user"));
		dSource.setPassword(p.getProperty("db_password"));
		dSource.setUrl(p.getProperty("db_uri"));

		this.source = new SimpleDataSourceWrapper(dSource);

		if (p.getProperty("db_filepath") != null)
		{
			File file_source = new File(p.getProperty("db_filepath"));
			this.fileSource = file_source;
		}
		else
		{
			logger.warn("db_filepath is missing");
		}
	}

	@Override
	public void beginTx() throws DatabaseException
	{
		getConnection();
		try
		{
			if (inTransaction)
			{
				logger.error("BeginTx failed: transaction already begun");
				throw new DatabaseException("BeginTx failed: transaction already begun");
			}
			connection.setAutoCommit(false);
			inTransaction = true;
			logger.debug("begin transaction");
		}
		catch (SQLException sqle)
		{
			logger.error("beginTx failed: " + sqle.getMessage());
			throw new DatabaseException(sqle);
		}
	}

	@Override
	public boolean inTx()
	{
		return inTransaction;
	}

	@Override
	public void commitTx() throws DatabaseException
	{
		try
		{
			if (!inTransaction) throw new DatabaseException("commitTx failed: no active transaction");
			connection.commit();
			connection.setAutoCommit(true);
			inTransaction = false;
			// FIXME in case of hsqldb we need to checkpoint
			// if(this.source.getDriverClassName().contains("hsql"))
			// this.executeQuery("checkpoint");
			logger.info("commited transaction");
		}
		catch (SQLException sqle)
		{
			logger.error("commitTx failed: " + sqle.getMessage());
			throw new DatabaseException(sqle);
		}
		finally
		{
			closeConnection();
		}
	}

	@Override
	public void rollbackTx() throws DatabaseException
	{
		try
		{
			if (!inTransaction) throw new DatabaseException("rollbackTx failed: no active transaction");
			connection.rollback();
			connection.setAutoCommit(true);
			inTransaction = false;
			logger.info("rolled back transaction on " + this.connection.getMetaData().getURL());
		}
		catch (SQLException sqle)
		{
			logger.error("rollbackTx failed: " + sqle.getMessage());
			throw new DatabaseException(sqle);
		}
		finally
		{
			closeConnection();
		}
	}

	@Override
	public void close() throws IOException
	{
		closeConnection();
	}

	@Override
	public EntityManager getEntityManager()
	{
		throw new UnsupportedOperationException();
	}

	/** The jndi to a data source */
	DataSourceWrapper source = null;

	/** The current JDBC connection of this database (only when in transaction) */
	Connection connection;

	/** Flag to indicate whether the database is in a transaction */
	boolean inTransaction = false;

	int openconnections = 0;

	/** Ticket to indicate a private transaction */
	String privateTransaction = null;

	public JDBCDatabase(DataSourceWrapper source)
	{
		this.source = source;
	}

	/** open the connection (if not already) */
	@Override
	public Connection getConnection() throws DatabaseException
	{
		if (source == null)
		{
			// this JDBCDatabase has been created with just a connection and no
			// source
			// so, there should be exactly 1 active connection that is handled
			// by the FrontController
			// System.out.println("No datasource, immediatly returning connection");
			return connection;
		}

		try
		{
			if (connection == null || connection.isClosed())
			{
				openconnections++;
				connection = source.getConnection();
				logger.debug(this + "opened database connection, connectioncount=" + openconnections
						+ ", count in pool: " + this.source.countOpenConnections() + "/" + source.getMaxActive());
				connection.setAutoCommit(true); // restore default
			}
			return connection;
		}
		catch (Exception sqle)
		{
			logger.error("Cannot open connection: " + sqle.getMessage());
			throw new DatabaseException(sqle);
		}
	}

	public DataSourceWrapper getSource()
	{
		return source;
	}

	/**
	 * close the connection (if not in transaction)
	 * 
	 * @throws DatabaseException
	 */
	protected void closeConnection()
	{
		if (source == null)
		{
			// this JDBCDatabase has been created with just a connection and no
			// source
			// so, there should be exactly 1 active connection that is handled
			// by the FrontController
			// do not close this connection here!
			// System.out.println("No datasource, not closing connection");
			return;
		}

		if (inTransaction)
		{
			logger.debug("Didn't close connection: transaction active");
		}
		else
		{
			if (connection != null)
			{
				try
				{
					connection.setAutoCommit(true); // restore default
					if (!connection.isClosed()) connection.close();
					connection = null;
					openconnections--;
					logger.debug(this + "closed connection back to pool, connectioncount=" + openconnections
							+ ", open connections in pool: " + source.countOpenConnections() + "/"
							+ source.getMaxActive());
				}
				catch (Exception sqle)
				{
					// System.err.println(this+"Cannot close connection: " +
					// sqle.getMessage());
					logger.error(this + "Cannot close connection: " + sqle.getMessage());
				}
			}
		}
	}

	/**
	 * Closes a statement.
	 * 
	 * @param stmt
	 */
	public static void closeStatement(Statement stmt)
	{
		try
		{
			if (stmt != null) stmt.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			logger.warn("closeStatement(): " + e);
		}
	}

	@Override
	public void flush()
	{
		// noop
	}

	@Override
	public void createTables() throws DatabaseException
	{
		this.executeSqlFile("/create_tables.sql");
	}

	@Override
	public void updateTables()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void dropTables() throws DatabaseException
	{
		// this.executeSqlFile("/drop_tables.sql");
	}

	@Override
	public void loadExampleData(ExampleData exampleData) throws DatabaseException
	{
		exampleData.load(this);
	}

	private void executeSqlFile(String filename) throws DatabaseException
	{
		Connection conn = null;
		Statement stmt = null;
		try
		{
			conn = this.getConnection();
			StringBuilder create_tables_sqlBuilder = new StringBuilder();

			InputStream fis = this.getClass().getResourceAsStream(filename);
			BufferedReader in = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
			try
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					create_tables_sqlBuilder.append(line).append('\n');
				}
			}
			finally
			{
				in.close();
			}
			stmt = conn.createStatement();
			for (String command : create_tables_sqlBuilder.toString().split(";"))
			{
				if (command.trim().length() > 0) stmt.executeUpdate(command + ";");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new DatabaseException(e);
		}
		finally
		{
			if (stmt != null) try
			{
				stmt.close();
			}
			catch (SQLException e)
			{
				logger.error(e);
			}
		}
	}
}