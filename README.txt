README.txt
==========

This project uses a single shell script, `runme.sh`, to compile and run all
microservices required for the assignment and execute workload files.

The script assumes a Unix environment (bash).

----------------------------------------------------------------------
Prerequisites
----------------------------------------------------------------------

- Java (JDK) installed and available on PATH
- Bash shell
- None of the files or directories have been moved (please keep all
    source code, .jar files, and config files where you found them)

When you first open the project, the script needs to be given execution
permission in order to run:

  chmod +x runme.sh

----------------------------------------------------------------------
Flags
----------------------------------------------------------------------

./runme.sh -c   Compile all Java source files

     Compile all source code with their respective .jar files, compiled
     results will be placed into the ./compiled directory

     All the required files for compiling are included in the project
     submission, no additional installation required.

--------------------------------------------------------------------

./runme.sh -u   Run UserService

     Starts the UserService using the configuration in `config.json`.
     Does NOT run in the background, will need its own terminal windows.

--------------------------------------------------------------------

./runme.sh -p   Run ProductService

     Starts the ProductService using the configuration in `config.json`.
     Does NOT run in the background, will need its own terminal windows.

--------------------------------------------------------------------

./runme.sh -i   Run ISCS

     Starts the ISCS service using the configuration in `config.json`.
     Does NOT run in the background, will need its own terminal windows.

--------------------------------------------------------------------

./runme.sh -o   Run OrderService

     Starts the OrderService using the configuration in `config.json`.
     Does NOT run in the background, will need its own terminal windows.

--------------------------------------------------------------------

./runme.sh -a   Run ALL microservices

     Starts UserService, ProductService, ISCS, and OrderService
     at the same time.

     The terminal will be locked while services are running.
     Kill it with Ctrl+C.

     If the project has not been compiled yet, this flag will automatically
     trigger compilation first.

     This was mostly written as a testing convenience, you do not
     have to use it.

--------------------------------------------------------------------

./runme.sh -w workload.txt   Run a workload file

     Executes a workload file using WorkloadParser.
     Replace 'workload.txt' with any other workload file in the same
     directory as the script (project root)

     Requires all microservices to already be running (e.g., via -a).

--------------------------------------------------------------------

./runme.sh -r   Remove Database

     Deletes the databases for User and Product Services:
       - users.db
       - products.db

     This wipes the slate clean for testing. If you run this while their
     respective services are running elsewhere, you will need to restart
     that service to re-initialize the database.

----------------------------------------------------------------------
Example Workflow
----------------------------------------------------------------------

1. Reset databases:
     ./runme.sh -r

2. Compile project:
     ./runme.sh -c

3. Start microservices:
     ./runme.sh -a

4. Run workload in a second terminal:
     ./runme.sh -w comprehensive_workload.txt

5. Stop services:
     Press Ctrl+C

----------------------------------------------------------------------
End of README
----------------------------------------------------------------------
