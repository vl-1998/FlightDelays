package it.polito.tdp.extflightdelays.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graphs;
import org.jgrapht.event.ConnectedComponentTraversalEvent;
import org.jgrapht.event.EdgeTraversalEvent;
import org.jgrapht.event.TraversalListener;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.BreadthFirstIterator;

import it.polito.tdp.extflightdelays.db.ExtFlightDelaysDAO;

public class Model {
	//partiamo dal grafo che vogliamo creare, in questo caso semplice pesato non orientato
	private SimpleWeightedGraph <Airport, DefaultWeightedEdge> grafo;
	private Map <Integer, Airport> idMap;
	private ExtFlightDelaysDAO dao;
	//mappa per salvare l'albero di visita e modellare le relazioni padre figlio
	private Map <Airport, Airport> visita = new HashMap<>(); //modello il fatto che a1 è raggiungibile da a2
	
	public Model () {
		//creiamo l'idmap
		idMap = new HashMap <>();
		dao = new ExtFlightDelaysDAO();
		//andiamo a riempire l'idMap
		this.dao.loadAllAirports(idMap);
	}
	
	//metodo per creare il grafi
	//ricevera un parametro dall'esterno, il parametro delle compagnie aeree
	public void creGrafo (int x) {
		this.grafo= new SimpleWeightedGraph <> (DefaultWeightedEdge.class);
		//aggiungiamo i vertici, devono rispettare il vincolo
		//scorro tutti gli aeroporti nell'id Map e faccio una query al DAO per recuperare tutti gli aeroporti
		//che rispettano la condizione
		for(Airport a : idMap.values()) {
			if (dao.getAirlinesNumber(a) > x) {
				//inserisco l'aeroporto come vertice
				this.grafo.addVertex(a);
			}
		}
		
		//mi faccio dare dal DAO le rotte dai vari aeroporti con gia calcolato il peso
		for (Rotta r: dao.getRotte(idMap)) {
			//essendo il grafo non orientato dobbiamo considerare le rotte sia da A a B, sia da B a A
			//farsi dare tutte le rotte dal DAO, poi le scrollo e qui controllo i doppioni, nel caso di doppioni
			//aggiorno il peso
			
			//dobbiamo controllare che gli aeroporti ci siano nel grafo
			//aggiungo l'arco solo se i due vertici sono nel grafo
			if (this.grafo.containsVertex(r.getA1()) && this.grafo.containsVertex(r.getA2())) {
				
				DefaultWeightedEdge e = this.grafo.getEdge(r.a1, r.a2);
			
				if (e==null) {//non c'è ancora, lo vado ad inseire
					Graphs.addEdgeWithVertices(grafo, r.getA1(), r.getA2(), r.getPeso());
				} else { //l'arco c'era gia
					double pesoVecchio = this.grafo.getEdgeWeight(e); //restituisce il peso gia assegnato all'arco
					double pesoNuovo = pesoVecchio + r.getPeso();
				
					this.grafo.setEdgeWeight(e, pesoNuovo);//aggiorno il peso di un determinato arco
				}
			}
		}
		
	}
	
	public int vertexNumber() {
		return this.grafo.vertexSet().size();
	}
	public int edgeNumber() {
		return this.grafo.edgeSet().size();
	}
	
	public Collection<Airport> getAeroporti() {
		return this.grafo.vertexSet();
	}
	
	//Metodo che ci ritorni una lista di aeroporti che moedella il percorso tra due aeroporti
	//se la lista sara nulla, i due aeroporti non sono connessi
	public List <Airport> trovaPercorso (Airport a1, Airport a2){
		List <Airport> percorso = new ArrayList <>();
		
		//per capire se due aeroporti sono connessi e estrapolarne il percorso
		//visito il grafo e man mano che lo visito tengo traccia dell'albero di visita
		//non cambia molto in questo caso tra ampiezza e profondita. Non basta attraversare il grafo
		//con un iteratore, ma bisogna agganciare al grafo un traversal Listener
		//ci permette di definire un metodo che viene richiamato ogni volta che la visita attraversa un determinato arco del grafo
		
		//prima di aggiungere il TraversalListener, aggiungo a visite la "radice" del mio albero di visita
		visita.put(a1, null);
		
		//definisco l'iteratore in ampiezza
		BreadthFirstIterator <Airport, DefaultWeightedEdge> it = new BreadthFirstIterator <>(this.grafo,a1);
		//faccio partire la visita, ma aggancio un traversal listener, per essere notificati ogni volta che attraversiamo un arco
		it.addTraversalListener(new TraversalListener <Airport, DefaultWeightedEdge>(){

			@Override
			public void connectedComponentFinished(ConnectedComponentTraversalEvent e) {}

			@Override
			public void connectedComponentStarted(ConnectedComponentTraversalEvent e) {}
			
			@Override
			//quando attraversiamo un arco salviamo la relazione tra il nodo sorgente e il nodo di arrivo della mappa 
			public void edgeTraversed(EdgeTraversalEvent<DefaultWeightedEdge> e) {
				//prendiamo il nodo sorgente e il nodo destinazione dell'arco che stiamo attraversando
				Airport sorgente = grafo.getEdgeSource(e.getEdge()); 
				//stessa cosa con la destinazione
				Airport destinazione = grafo.getEdgeTarget(e.getEdge()); 
				
				//se la visita non contiene la destinazione, ma contiene il nodo sorgente
				if (!visita.containsKey(destinazione) && visita.containsKey(sorgente)){
					//possiamo modellare la relazione padre figlio tra sorgente e destiazione. la destinazione
					//si raggiunge da sorgente
					visita.put(destinazione, sorgente); //essendo un grafo non orientato, sono interscambiabili
				} else if (!visita.containsKey(sorgente) && visita.containsKey(destinazione)){
					visita.put(sorgente, destinazione);
				}
			}

			@Override
			public void vertexTraversed(VertexTraversalEvent<Airport> e) {}

			@Override
			public void vertexFinished(VertexTraversalEvent<Airport> e) {}
			
		});
		
		//visitiamo il grafo, ogni volta che attraverseremo un arco ne terremo traccia con il TraversalListener
		while (it.hasNext()) {
			it.next();
		}
		
		//controlliamo se i due aeroporti sono collegati o no, perche se l'albero di visita non contiene 
		//la partenza o la destinazione i due non sono collegati
		if (!visita.containsKey(a1) || !visita.containsKey(a2)) {
			//i due non sono collegati
			return null;
		}
		
		//ora dobbiamo salvare il percorso
		Airport step = a2;
		//risaldo l'albero fino alla partenza
		while (!step.equals(a1)) {
			percorso.add(step);
			step = visita.get(step); //risalgo di uno step fin quando non ritrovo la partenza
		}
		
		//quando lo step è uguale alla partenza a1 non entro neanche nel while 
		percorso.add(a1);//altrimenti non veniva aggiunta
		
		return percorso;
		
	}

}
