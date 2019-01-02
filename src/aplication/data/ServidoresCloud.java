package aplication.data;

import aplication.threads.AtribuirServidores;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ServidoresCloud {

    private class Informacao{
        public ArrayList<ServidorCloud> servidores;
        public ArrayList<Proposta> propostas;
        public int servidores_disponiveis;
        public ReentrantLock lockServidores = new ReentrantLock();
        public ReentrantLock lockPropostas = new ReentrantLock();
        public ReentrantLock lock_Servidores_Propostas = new ReentrantLock();
        public Condition not_servidores_propostas = lock_Servidores_Propostas.newCondition();
        
        public Informacao(ArrayList<ServidorCloud> servidores){
            this.servidores= servidores;
            this.servidores_disponiveis=0;
            this.propostas= new ArrayList<Proposta>();
        }
        
    }
        
    private HashMap<String,Informacao> informacao;
    private ReentrantLock lock = new ReentrantLock();
    private int proxId;
    Utilizadores utilizadores;
    
    private ReentrantLock lock_servidores_propostas = new ReentrantLock();
    private Condition c1 = this.lock_servidores_propostas.newCondition();

    
    public ServidoresCloud(Utilizadores utilizadores) {
        this.utilizadores= utilizadores;
        this.informacao = new HashMap<String,Informacao>();
        this.proxId=0;
    }

    /*
        Regista o nº de servidores fixos: nomeServidor, taxa a pagar, licitação mínima
    */
    public synchronized void registarServidor(String nome, double taxa, double licitacaoMinima){

        ServidorCloud servidorCloud = new ServidorCloud(nome,taxa,proxId,licitacaoMinima);

        if(this.informacao.isEmpty() || !this.informacao.containsKey(nome)){
            ArrayList<ServidorCloud> servidores= new ArrayList<ServidorCloud>();
            servidores.add(servidorCloud);
            Informacao info = new Informacao(servidores);
            this.informacao.put(nome, info);
            
        //Iniciar thread de atribuir o servidor às propostas
            AtribuirServidores atribuirservidores = new AtribuirServidores(nome,this.utilizadores,this);
            Thread atribuirservidoresThread = new Thread(atribuirservidores);
            atribuirservidoresThread.start();
        }
        else{
            this.informacao.get(nome).servidores.add(servidorCloud);
        }

        proxId++;
        this.informacao.get(nome).servidores_disponiveis++;
        
        

    }

    /*
        Recebe uma lista de servidores, de um determinado tipo
            Procura servidores (ocupados) e reservados por leilão.
                Se encontrar algum, retorna o que reservou com menor licitação, 
                para lhe desalocar o servidor: "2-nomeServidor idServidor"
                Else retorna null, ou seja, nao ha servidores reservados por leilão. 
    */
    
    public String leilaoParaPedido(ArrayList<ServidorCloud> servidorClouds){
        double taxaMinima = Double.MAX_VALUE;
        int id = -1;
        for(int i = 0; i < servidorClouds.size();i++){
            if(servidorClouds.get(i).isOcupado() && servidorClouds.get(i).isLeilao()){
                if(servidorClouds.get(i).getTaxaLeiloada() < taxaMinima){
                    taxaMinima = servidorClouds.get(i).getTaxaLeiloada();
                    id = i;
                }
            }
        }

        if (taxaMinima != Double.MAX_VALUE){
            servidorClouds.get(id).setLeilao(false);
            return "2-"+servidorClouds.get(id).getNome()+" "+servidorClouds.get(id).getId();
        }
        else return null;
    }

    /*
        Efetuar reserva a pedido, para um determinado servidor:
            Se encontrar algum desocupado, retorna "1-nomeServidor idServidor"
            Senao encontrar nenhum, efetua a função leilaoParaPedido (para verificar 
            se existe um de leilao para lhe retirar o servidor) 
                Se for !=null retorna o resultado desta função
                Else retorna TodosServidoresIndisponiveis
            Se esse servidor nao existir, retorna ServidorInexistente
    */
    public String reservarPedido(String nomeServidor){
        ArrayList<ServidorCloud> servidorClouds=null;

        this.lock.lock();
        try{
            Informacao informacao = this.informacao.get(nomeServidor);
                servidorClouds = informacao.servidores;
                informacao.lockServidores.lock();
                this.lock.unlock();
                try{
                    for(ServidorCloud servidorCloud : servidorClouds){
                            if(!servidorCloud.isOcupado()) {
                                servidorCloud.setOcupado(true);
                                return "1-"+nomeServidor+" "+servidorCloud.getId();
                            }
                    }
                    String leilaoParaPedido = leilaoParaPedido(servidorClouds);
                    if (leilaoParaPedido != null) return leilaoParaPedido;

                    return "TodosServidoresIndisponiveis";
                }finally{
                    informacao.lockServidores.unlock();
                }
                
        }
        catch(NullPointerException e){
            this.lock.unlock();
            return "ServidorInexistente";
        }
        
    }
    
    /*
        Efetuar reserva a leilão, para um determinado servidor, com uma licitação
            Procura por um servidor desocupado: 
                Se encontrar um desocupado e a sua licitação for maior que a licitação mínima
                retorna "nomeServidor idServidor"
                Se não encontrar um desocupado e a sua licitação for maior que a licitação mínima
                efetua a função registarProposta, e retorna ServidoresOcupados
                Se encontrou servidores desocupados e a sua licitação for menor que a licitação mínima
                retorna LicitacaoBaixa
                
                Se esse servidor nao existir, retorna ServidorInexistente    
    */

    public String reservarLeilao(String nomeServidor, String email, double licitacao){
        ArrayList<ServidorCloud> servidorClouds =null;

        this.lock.lock();
        try{
            Informacao informacao = this.informacao.get(nomeServidor);
            servidorClouds = informacao.servidores;
            
            informacao.lockServidores.lock();
            boolean tentouLicitar = false;
            try{
                double licitMin;
                for(ServidorCloud servidorCloud : servidorClouds){
                    if(!servidorCloud.isOcupado()) {

                    tentouLicitar = true;
                    licitMin = servidorCloud.getLicitacaoMinima();
                        if(licitacao >= licitMin) {

                            servidorCloud.setTaxaLeiloada(licitacao); //TIRAR ISTO, SE RETIRARMOS ESTA VARIÁVEL
                            servidorCloud.setLeilao(true);
                            servidorCloud.setOcupado(true);
                            return nomeServidor+" "+servidorCloud.getId();
                        }
                    }
                }
                
                }finally{
                    informacao.lockServidores.unlock();
                }
                if(!tentouLicitar) {
                    registarProposta(nomeServidor,email,licitacao);
                    return "ServidoresOcupados";
                }
                
                return  "LicitacaoBaixa";

            }finally{
                this.lock.unlock();
                return "ServidorInexistente";
            } 
    }
    
    /*
        Efetua o registo da proposta de um determinado utilizador, para um tipo de servidor (através da função anterior)
            Cria uma proposta com uma licitação e um email.
                Verifica se o HashMap de propostas está vazio ou se ainda não existem 
                propostas para esse tipo de servidor:
                    - Caso se verifique, adiciona a 1º proposta para esse servidor;
                        Aqui, é iniciada a Thread AtribuirServidores para esse tipo de servidor 
                        (thread para tentar alocar servidores disponíveis a propostas existentes)
                    - Caso existam propostas, verifica se já existe uma proposta
                    daquele utilizador para aquele tipo de servidor:
                        -Se já fez uma proposta, tal é removida e inserida a nova.
                        -Senão, apenas é inserida a atual proposta.
                        
                    - No fim, notifica a condição c1 (foi inserida uma proposta)
    */
 
    public void registarProposta(String nomeServidor, String email, double licitacao){
        Proposta proposta = new Proposta(licitacao,email);
        ArrayList<Proposta> p = null;
        Informacao informacao =  this.informacao.get(nomeServidor);
        informacao.lockPropostas.lock();
        try{
            informacao.lock_Servidores_Propostas.lock();
            Proposta pr = verficarProposta(email,p);
            if(pr!=null){  p.remove(pr); }
            p.add(proposta);
            informacao.not_servidores_propostas.signal(); //NOTIFICAR QUANDO HA UMA PROPOSTA
            informacao.lock_Servidores_Propostas.unlock();
        }finally{
            informacao.lockPropostas.unlock();
        }
    } 
    

    /*
        Verifica se uma determinada proposta existe num conjunto de propostas, 
        registada por um certo utilizador, com o email dado (utilizada na função anterior)
    */
    public Proposta verficarProposta(String email, ArrayList<Proposta> propostas){
        Proposta res = null;

        for(Proposta p: propostas) {
            if (p.getEmail().equals(email)) {
                res = p;
                return res;
            }
        }
        return res;
    }

    /*
        Consultar servidores disponíveis:
            Procura em todo o HashMap de Servidores e retorna para todos os tipos de servidor:
                "-nomeServidor numeroDisponiveis numeroLeiloes taxaFixa licitacaoMinima-..."
    */
    public String consultarDisponiveis(){
        StringBuilder stringBuilder = new StringBuilder();
        int nDisponiveis, nLeiloes;
        this.lockServidores.lock();
        try{
            for (Informacao informacao : this.informacao.values()) {
                nDisponiveis = 0;
                nLeiloes = 0;
                String nome = null;
                double taxaFixa=0;
                double licitacaoMinima=0;
                for(ServidorCloud servidorCloud: informacao.servidores){
                    nome = servidorCloud.getNome();
                    taxaFixa = servidorCloud.getTaxaFixa();
                    licitacaoMinima = servidorCloud.getLicitacaoMinima();
                    if(!servidorCloud.isOcupado()) nDisponiveis++;
                    else {
                        if(servidorCloud.isLeilao()) nLeiloes++;
                    }
                }
                stringBuilder.append("-"+nome +" "+ nDisponiveis +" " + nLeiloes + " " +taxaFixa + " "+ licitacaoMinima );

            }
            return stringBuilder.toString();
        }finally{
            this.lockServidores.unlock();
        }
    }
    
    /*
        Retorna a taxa Fixa para um determinado tipo de servidor (utilizada na 
        Thread DescontarSaldo, para se saber quanto descontar ao saldo do cliente 
        por "cada hora".
    */
    public double taxaServidor(String nomeServidor){
        this.lockServidores.lock();
        ArrayList<ServidorCloud> servidores = this.informacao.get(nomeServidor).servidores;
        synchronized (servidores) {
            this.lockServidores.unlock();
            return servidores.get(0).getTaxaFixa();
        }
    }

    /*
        Desocupa um certo tipo de servidor, dando-lhe o seu id como argumento 
        (chamada na thread TratarCliente, quando a reserva de um determinado servidor
        é retirada a um utilizador)
           - Quando encontra o servidor com o id:
                - Se foi anteriormente reservado como leilão, põe essa variável a false
                  e a variável Ocupado a false
                - Senão só põe a variável Ocupado a false
           - No fim, notifica a condição c1 (servidor disponível)     
    */
    public void desocupaServidor(String nomeServidor,int id){
        this.lockServidores.lock();
        ArrayList<ServidorCloud> servidores = this.informacao.get(nomeServidor).servidores;
        synchronized (servidores){ 
            this.lock_servidores_propostas.lock();
            this.lockServidores.unlock();
            for(ServidorCloud sC : servidores){
                if(sC.isLeilao() && sC.getId()==id){
                    sC.setLeilao(false);
                    sC.setOcupado(false);
                }else if(sC.getId()==id){
                    sC.setOcupado(false);
                }
            }
            this.c1.signalAll(); // NOTIFICAR QUANDO HA SERVERES DIPONVEIS
            this.lock_servidores_propostas.unlock();
        }
    }
    
    
    /*
      Função utilizada como argumento na função desocupaServidor, pois, dado um
        id de reserva, retorna o seu tipo de servidor (argumento na função anterior)
    */
    //TIRAR PARTIDO DO ID do servidor (mudar interface??)
    public String servidorPorId(int id){
        this.lockServidores.lock();
        try{
            for(Informacao informacao : this.informacao.values()){
                for(ServidorCloud s : informacao.servidores){
                    if(s.getId()==id) return s.getNome();
                }
            }
           return null;
        }
        finally{
            this.lockServidores.unlock();
        }
    }
    
    /*
        Função de consultar propostas existentes para um determinado tipo de servidor:
            - Tipo de servidor inexistente, retorna: ServidorInexistente
            - Não há propostas para esse tipo de servidor, retorna: Naohapropostas
            - Senao retorna: "-Licitacao Email-..."
    */
    public String propostasPorServidor(String nomeServidor){
        StringBuilder res = new StringBuilder();
        this.lockPropostas.lock();
        this.lockServidores.lock();
        ArrayList<Proposta> propostas = this.informacao.get(nomeServidor).propostas;
        ArrayList<ServidorCloud> servidores = this.informacao.get(nomeServidor).servidores;
        
        if(servidores==null) {
            this.lockServidores.unlock();
            this.lockPropostas.unlock();
            return "ServidorInexistente";
        }
        else if(propostas==null) {
            this.lockServidores.unlock();
            this.lockPropostas.unlock();
            return "Naohapropostas";
        }
        else{
            synchronized (servidores){
                this.lockServidores.unlock();
                synchronized (propostas){
                    this.lockPropostas.unlock();
                    for(Proposta p : propostas){
                        res.append("-"+p.getLicitacao() + " " + p.getEmail());
                    }
                    return res.toString();
                }
            }
        }
    }
    
    
    ///*****************************************************//
    public int servidoresDisponiveis(ArrayList<ServidorCloud> servidores){
        int s_disponiveis=0;
        for(ServidorCloud sC : servidores){
            if(!sC.isOcupado()) s_disponiveis++;
        }
        return s_disponiveis;
    }
    
    // Servidor -> ocupado; Eliminar Proposta escolhida; Adicionar reserva a cliente. 
    public String atualizaInformacao(ArrayList<ServidorCloud> servidores,ArrayList<Proposta> propostas){
        double licitacao=0;
        int indiceMelhorProposta=0;
        String email=null;
        ServidorCloud servidorEscolhido=null;
        
        for(ServidorCloud sC : servidores){
            if(!sC.isOcupado()) {
                sC.setOcupado(true);
                servidorEscolhido=sC;
            }
        }
        for(int i=0; i<propostas.size();i++){
            Proposta proposta = propostas.get(i);
            if(proposta.getLicitacao()>licitacao) {
                licitacao=proposta.getLicitacao();
                indiceMelhorProposta=i;
                email=proposta.getEmail();
            }
        }
        
        propostas.remove(indiceMelhorProposta);
        
        return email + "-"+ servidorEscolhido.getNome() + " " + servidorEscolhido.getId();
        
    }
    
   /* public String servidorParaProposta(ArrayList<ServidorCloud> servidores) throws InterruptedException{
        String res=null;
        
        synchronized (servidores){
            while((servidoresDisponiveis(servidores))==0){
                servidores.wait();
            }

            this.lockPropostas.lock();

            ArrayList<Proposta> propostas = this.propostas.get(servidores.get(0).getNome());
            synchronized (propostas) {
                this.lockPropostas.unlock();
                while (propostas == null || propostas.size() == 0) {
                    propostas.wait();
                }
                res = atualizaInformacao(servidores, propostas);
                
            }
        }
        
        return res;
    }
*/
    
    /*
      Se houver servidores disponiveis, segundo um tipo de servidor, e propostas
        existentes para esse tipo de servidor, efetua a função atualizaInformação
        (pôr o servidor a ocupado, eliminar a proposta e fazer a reserva para um cliente)
        Caso contrario, adormece
    */
    public String servidorParaProposta(String nomeServidor) throws InterruptedException{
        String res=null;
        this.lockServidores.lock();
        this.lockPropostas.lock();
        ArrayList<ServidorCloud> servidores = this.informacao.get(nomeServidor).servidores;
        ArrayList<Proposta> propostas = this.informacao.get(nomeServidor).propostas;
            
        this.lock_servidores_propostas.lock();
           try{ 
                this.lockServidores.unlock();
                this.lockPropostas.unlock();
                while((servidores==null || servidoresDisponiveis(servidores)==0 ) || (propostas==null || propostas.size()==0)){
                    System.out.println("Fiquei a dormir!");
                    this.c1.await();
                }
                System.out.println("Acordei");
                res = atualizaInformacao(servidores,propostas);
           }finally{
            this.lock_servidores_propostas.unlock();
           }
        
        return res;
    }
}