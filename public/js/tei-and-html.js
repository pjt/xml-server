$(function(){
   $.fn.asTEI = function(){
      var node = this.get(0);
      if (node.nodeType === 8){ // comment node
         return '<!--' + node.nodeValue.replace(/&/g,'&amp;') + '-->';
      } else if (node.nodeType === 3){ // text node
         return node.nodeValue.replace(/&/g,'&amp;');
      } else {
         var tei, tag, atts;
         tei = [], atts = [];
         $.each(node.getAttribute("class").split(/\s/), function(i, val){
            if (/^tei-/.test(val)){
               tag = val.replace(/^tei-/,'');
            }
            if (/^teiatt-/.test(val)){
               atts.push(val.replace(/^teiatt-/,'').replace(/__/g,' ').split('='));
            }
         });
         tei.push("<"+tag);
         tei.push($.map(atts, function(val,i){ 
                                 return ' '+val[0]+'="'+val[1]+'"'; 
                              }).join(""));
         tei.push(">");
         
         // recurse
         $.each(node.childNodes, function(i, val){ tei.push($(val).asTEI()); });
         
         // close, or self-close
         tei.push(node.childNodes.length ? "</"+tag+">" : 
                                                tei.pop().replace(/>$/,"/>"));
         return tei.join("");
      }
   }

   function idtoPath(p){
      return p.replace(/^path/,'')
               .split("--").join("/")
               .split("_-").join("[")
               .split("-_").join("]");
   }

   $.fn.isRoot = function(){
      return idtoPath(this.get(0).id).split("/") === 2;
   }

   function getSpan(el){
      var el = $(el);
      if (el.is('span')){
         return el;
      } else {
         var parent = el.parent();
         while (parent.size() && !parent.is('span')){
            parent = parent.parent();
         }
         return parent;
      }
   }

   $.fn.addParent = function(){
      var p = this.parent();
      if (p.size() && p.is('span') && !p.isRoot()){
         $("#facebox").find(".footer")
            .prepend($("<a class='edit-parent'>Edit parent &lt;"+ 
                           p.attr('class').match(/tei-([^ ]+)/)[1] +"&gt;</a>")
                        .click(function(e){
                                 e.preventDefault();
                                 p.edit();
                              }));
      }
      return this;
   }

   var STATE = {
      current: {
         el: null,
         unregister: function(){
            if (this.el){
               this.el.removeClass("editing");
               this.el = null;
            }
         },
         register: function(node){
            this.unregister();
            this.el = node.addClass("editing");
         }
      },
      edits: {
         count: 0,
         inc:     function(){ this.count++; this.display(); },
         reset:   function(){ this.count = 0; this.display(); },
         display: function(){ $("#inc").text(this.count); }
      },
      search: {
         index:   null,
         reset:   function(){ this.index = null; },
         next:    function(str,q){
            var offset = str.slice(this.index || 0).search(new RegExp(q,"i"));
            if (offset === -1 && this.index){
               this.reset();
               return this.next(str,q);
            } else if (offset === -1){
               this.reset();
               return false;
            } else { 
               this.index += offset + q.length;
               return this.index;
            }
         }
      }
   }
   $(document).bind('close.facebox', function(){
         STATE.current.unregister();
      });

   $.fn.addSearch = function(){
      // adds search field to textarea of >= 5 lines
      var $TA = this, TA = this.get(0), 
          num_lines = $TA.val().split("\n").length;
      if (num_lines < 6) 
         return $TA;
      $("<input type='text' value='Search' />")
        .css({width: "25%"})
        .focus(function(){ if (this.value === "Search") this.value = ""; })
        .blur(function(){ if (this.value === "") this.value = "Search"; })
        .keydown(function(e){
            if (e.keyCode != 13){
               STATE.search.reset();
            } else {
               e.preventDefault();
               var srch = $(this), q = srch.val(), val = $TA.val();
               var offset = STATE.search.next(val,q);
               if (offset){
                  TA.selectionStart = offset - q.length;
                  TA.selectionEnd = offset;
                  TA.scrollTop = TA.scrollHeight * (offset / val.length) 
                                    - ($TA.height() / 2);
                  TA.focus();
               } else {
                  srch.addClass("no-match");
                  setTimeout(function(){ srch.removeClass("no-match"); }, 350);
               }
            }
         })
        .insertAfter($TA);
      $TA.change(function(){STATE.search.reset();});
      return $TA;
   }

   $.fn.edit = function(){
      var el = this.get(0), $el = this,
         edit = document.createElement("textarea"),
         tei = this.asTEI();
      $(edit).val(tei).css({
            width: $(document.body).width() / 1.1,
            maxHeight: $(window).height() / 1.5
         })
         .attr('rows', 1 + 2 * tei.split('\n').length)
         .keydown(function(e){
               if (e.shiftKey && e.keyCode == 13){
                  e.preventDefault();
                  $.facebox.waiting(true);
                  $.ajax({
                     url: "/edit",
                     type: "POST",
                     contentType: 
                        "application/x-www-form-urlencoded;charset=UTF-8",
                     data: {
                        select: idtoPath(el.id),
                        update: edit.value,
                        "post-style": "tei-to-html"
                     },
                     dataType: "html",
                     success: 
                        function(data){
                           $.facebox.close();
                           var pa = el.parentNode;
                           pa.innerHTML = data;
                           // remove containing element, which is  now 
                           // superseded by Ajax response; this works 
                           // around outerHTML not being implemented in Ffx
                           pa.parentNode.replaceChild(pa.childNodes[0],pa);
                           STATE.edits.inc();
                        },
                     error: function(xhr, status, error){
                        $.facebox.waiting(false);
                        fbxError(getErrMsg(xhr.responseText));
                     }
                  });
               }
            });
      $.facebox(function(){
            $.facebox(edit);
            $(edit).addSearch();
            STATE.current.register($el);
            $el.addParent();
            edit.focus();
         });
   }

   $(document).dblclick(function(e){
         getSpan(e.target).edit();
      });

   // facebox mods: kill default footer, add legend
   $(document).bind('reveal.facebox', function(){
         $("#facebox").find(".footer").empty()
            .append("<span class='legend'>Esc to close |"+
                        " Shift+Return to submit</span>");
      });

   var getErrMsg = (function(){
         var specials = [
            {regex: /Form too large/i, 
             message: "The amount of text you're submitting is too large; "+ 
                        "is there a way to still make your edit while "+
                        "selecting less text?",
             combine: function(msg,err) { return msg; }
            },
            {regex: /Failed to compile/i,
             message: "The XML you're submitting is not well-formed. Please "+
                      "check to make sure all tags are properly nested and ampersands "+
                      "are escaped, etc.",
             combine: function(msg,err) { return msg + err; }
            }
         ]
         return function(str){
            // gets message from HTML string, assuming message is
            // in /html/body
            var msg = str.match(/<body(?:[^>]*>)((?:.|[\n\r])*)<\/body>/m);
            msg = msg ? msg[1] : str;
            $.each(specials, function(i, obj){
                  if (obj.regex.test(msg)){
                     msg = obj.combine("<p><em>"+ obj.message +"</em></p>", msg);
                     return false;
                  }
               })
            return msg;
         }
   })();

   function fbxError(str, flash_timing){
      var fbx = $("#facebox").find(".content");
      fbx.find("#err").remove();
      var err = $("<div id='err'>"+str+"</div>");
      if (fbx.find("textarea").size()){
        fbx.find("textarea:first").before(err);
      } else {
        fbx.prepend(err);
      } 
      if (!flash_timing){
         err.click(function(){err.hide('slow').remove();});
      } else {
         setTimeout(function(){
               err.hide('slow').remove();
            }, flash_timing);
      }
   }

   // set up control box at bottom
   $(document.body).append(
      $("<div id='control'>"+
            "<span id='gen-instruct'><em>To edit, double-click text</em></span>"+
         "<div id='right'>"+
            "<a href='/edit/commit' class='button' id='svn-ci'>Commit Changes</a>"+
            "<a href='/edit/diff' class='button' id='svn-diff'>See Changes"+
            " (<span id='inc'>0</span>)</a></div></div>"));
   // events
   $("#svn-diff").click(function(e){
         e.preventDefault();
         $.facebox.loading();
         $.ajax({
            url: "/edit/diff",
            type: "GET",
            dataType: "text",
            success: function(data){
               // little diff parser
               var cls, dff = data.replace(/&/g,"&amp;").replace(/</g,"&lt;")
                           .split(/\n/)
                           .map(function(v,i){
                              if (i < 4){ // first 4 lines are intro
                                 cls = "";
                              } else {
                                 cls = "alt" + (i % 2).toString() + " line ";
                                 cls += !/^(\+|-)/.test(v[0] || "") ? "" :
                                                v[0] === "+" ? "plus" : "minus";
                              }
                              return "<span class='"+cls+"'>"+v+"</span>";
                           });
               $.facebox("<pre class='diff'>"+ dff.join("") + "</pre>");
               $("#facebox .diff").css({maxHeight: $(window).height() * .7});
            },
            error: function(xhr, status, error){
               $.facebox("");
               fbxError(getErrMsg(xhr.responseText));
            }
         });
      });
   $("#svn-ci").click(function(e){
         e.preventDefault();
         if (STATE.edits.count === 0){
            $.facebox("No changes to submit, yet.");
         } else { 
            var msg = $("<p>Please provide a message to accompany your changes:</p>"+
                           "<textarea id='msg' cols='35' rows='8'/>");
            $.facebox(msg);
            $('#msg').focus()
               .keydown(function(e){
                  if (e.shiftKey && e.keyCode == 13){
                     e.preventDefault();
                     var $t = $(this);
                     if (!$t.val().replace(/^\s+|\s+$/g,"")){
                        fbxError("Provide a message!", 900);
                        return;
                     }
                     $.facebox.waiting(true);
                     $.ajax({
                        url: "/edit/commit",
                        type: "POST",
                        contentType: 
                           "application/x-www-form-urlencoded;charset=UTF-8",
                        data: {
                           msg: $t.val()
                        },
                        dataType: "html",
                        success: function(data){
                           STATE.edits.reset();
                           $.facebox.waiting(false);
                           $("#facebox .content").text("Changes submitted!");
                        },
                        error: function(xhr, status, error){
                           $.facebox.waiting(false);
                           fbxError(getErrMsg(xhr.responseText));
                        }
                     });
                  }
               });
         }
      });

   window.onbeforeunload = function(){
      if (STATE.edits.count){
         var m = "You currently have "+ STATE.edits.count.toString() +" edit";
            m += STATE.edits.count === 1 ? " " : "s ";
            m += "that won't be saved if you leave now.";
         return m;
      }
   }
   window.onunload = function(){
      $.ajax({url: "/edit/bounce", async: false});
   }

   // Draggable
   $.fn.draggle = function(mod_key){
      var msg;
      if (mod_key){
         msg = mod_key[0].toUpperCase().concat(mod_key.slice(1)) + "+";
         mod_key = mod_key === "control" ? "ctrlKey" : mod_key + "Key";
      }
      msg = (msg || "") + "Click to drag window.";
      return this.each(function(){
         var oX, oY, oOffset;      
         $(this).mousedown(function(e){
            if (mod_key && !e[mod_key]) return;
            if (/textarea|input|option/i.test(e.target.nodeName)) 
               return;
            oOffset = $(this).offset();
            oX = e.pageX, oY = e.pageY;
            $(this).mousemove(function(e){
               e.preventDefault();
               $(this).css({
                  left: oOffset.left - (oX - e.pageX), 
                  top: oOffset.top - (oY - e.pageY)
               });
            }).addClass("dragging");
         })
         .mouseup(function(){
            $(this).unbind("mousemove").removeClass("dragging");
         })
         .mouseover(function(){ window.status = msg; })
         .mouseout(function(){ window.status = ""; })
      }).addClass("draggable");
   }

   $(document).bind("reveal.facebox", function(){
      $("#facebox").draggle();
   });

});
 
