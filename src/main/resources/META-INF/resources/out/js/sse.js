(function(){var k;htmx.defineExtension("sse",{init:function(j){if(k=j,htmx.createEventSource==null)htmx.createEventSource=M},getSelectors:function(){return["[sse-connect]","[data-sse-connect]","[sse-swap]","[data-sse-swap]"]},onEvent:function(j,q){var z=q.target||q.detail.elt;switch(j){case"htmx:beforeCleanupElement":var B=k.getInternalData(z),G=B.sseEventSource;if(G)k.triggerEvent(z,"htmx:sseClose",{source:G,type:"nodeReplaced"}),B.sseEventSource.close();return;case"htmx:afterProcessNode":Z(z)}}});function M(j){return new EventSource(j,{withCredentials:!0})}function Y(j){if(k.getAttributeValue(j,"sse-swap")){var q=k.getClosestMatch(j,_);if(q==null)return null;var z=k.getInternalData(q),B=z.sseEventSource,G=k.getAttributeValue(j,"sse-swap"),J=G.split(",");for(var I=0;I<J.length;I++){let $=J[I].trim(),V=function(W){if(Q(q))return;if(!k.bodyContains(j)){B.removeEventListener($,V);return}if(!k.triggerEvent(j,"htmx:sseBeforeMessage",W))return;F(j,W.data),k.triggerEvent(j,"htmx:sseMessage",W)};k.getInternalData(j).sseEventListener=V,B.addEventListener($,V)}}if(k.getAttributeValue(j,"hx-trigger")){var q=k.getClosestMatch(j,_);if(q==null)return null;var z=k.getInternalData(q),B=z.sseEventSource,K=k.getTriggerSpecs(j);K.forEach(function(P){if(P.trigger.slice(0,4)!=="sse:")return;var X=function(H){if(Q(q))return;if(!k.bodyContains(j))B.removeEventListener(P.trigger.slice(4),X);htmx.trigger(j,P.trigger,H),htmx.trigger(j,"htmx:sseMessage",H)};k.getInternalData(j).sseEventListener=X,B.addEventListener(P.trigger.slice(4),X)})}}function Z(j,q){if(j==null)return null;if(k.getAttributeValue(j,"sse-connect")){var z=k.getAttributeValue(j,"sse-connect");if(z==null)return;f(j,z,q)}Y(j)}function f(j,q,z){var B=htmx.createEventSource(q);B.onerror=function(J){if(k.triggerErrorEvent(j,"htmx:sseError",{error:J,source:B}),Q(j))return;if(B.readyState===EventSource.CLOSED){z=z||0,z=Math.max(Math.min(z*2,128),1);var I=z*500;window.setTimeout(function(){Z(j,z)},I)}},B.onopen=function(J){if(k.triggerEvent(j,"htmx:sseOpen",{source:B}),z&&z>0){let I=j.querySelectorAll("[sse-swap], [data-sse-swap], [hx-trigger], [data-hx-trigger]");for(let K=0;K<I.length;K++)Y(I[K]);z=0}},k.getInternalData(j).sseEventSource=B;var G=k.getAttributeValue(j,"sse-close");if(G)B.addEventListener(G,function(){k.triggerEvent(j,"htmx:sseClose",{source:B,type:"message"}),B.close()})}function Q(j){if(!k.bodyContains(j)){var q=k.getInternalData(j).sseEventSource;if(q!=null)return k.triggerEvent(j,"htmx:sseClose",{source:q,type:"nodeMissing"}),q.close(),!0}return!1}function F(j,q){k.withExtensions(j,function(G){q=G.transformResponse(q,null,j)});var z=k.getSwapSpecification(j),B=k.getTarget(j);k.swap(B,q,z)}function _(j){return k.getInternalData(j).sseEventSource!=null}})();
