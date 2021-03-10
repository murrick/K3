window.apihost = "http://localhost:9090"
window.commandsHistory = [];


function showVersion(div) {
    $.getJSON(window.apihost + "/version", function(data){
        if(div) {
            div.innerHTML = data.result;
        }
    });
}