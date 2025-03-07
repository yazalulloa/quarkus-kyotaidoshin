import "external-svg-loader";
import Alpine from 'alpinejs'
import focus from '@alpinejs/focus'

window.addEventListener("popstate", (event) => {

  console.log("popstate event: ", event);
  window.location.reload();
});

window.limitInputToMaxLength = function (input) {
  if (input.maxLength && input.maxLength > 0) {
    input.oninput = () => {
      if (input.value.length > input.maxLength) {
        input.value = input.value.slice(0,
            input.maxLength);
      }
    }
  }
}

// function getCookie(name) {
//   const cookies = document.cookie.split('; ');
//   for (let i = 0; i < cookies.length; i++) {
//     const cookie = cookies[i].split('=')
//     if (cookie[0] === name) {
//       return decodeURIComponent(cookie[1]);
//     }
//   }
//   return null;
// }

// document.body.addEventListener("htmx:configRequest", function (configEvent) {
//
//   let token = getCookie('XSRF-TOKEN');
//   if (token != null) {
//     configEvent.detail.headers['X-XSRF-TOKEN'] = token;
//   }
//
// })

window.validateEmail = function (value) {

  if (value) {
    if (value.length > 0) {
      let atIndex = value.indexOf("@");
      let dotIndex = value.indexOf(".");

      if (atIndex > 0 && dotIndex > atIndex && dotIndex < value.length - 1) {
        return true;
      }
    }
  }

  return false;
}

window.sendEvent = function (id, eventName) {
  let elem = document.getElementById(id);
  if (elem) {
    elem.dispatchEvent(new CustomEvent(eventName));
  }
}

document.addEventListener("DOMContentLoaded", function () {
  // console.log("DOMContentLoaded");
  //initNav();
});

// function isNumeric(str) {
//   if (typeof str != "string") {
//     return false
//   } // we only process strings!
//   return !isNaN(str) && // use type coercion to parse the _entirety_ of the string (`parseFloat` alone does not do this)...
//       !isNaN(parseFloat(str)) // ...and ensure strings of whitespace fail
// }

window.sleep = function (ms) {
  //console.assert(isNumeric(ms));
  return new Promise(resolve => setTimeout(resolve, ms));
}

window.doAfterDelay = function (ms, callback) {
  setTimeout(() => {
    callback();
  }, ms);
}

document.addEventListener("securitypolicyviolation", (e) => {
  console.log(e.blockedURI);
  console.log(e.violatedDirective);
  console.log(e.originalPolicy);
  console.log(e.columnNumber)
  console.log(e.lineNumber)
});

window.elementIsVisibleInViewport = (el, partiallyVisible = false) => {
  const {top, left, bottom, right} = el.getBoundingClientRect();
  const {innerHeight, innerWidth} = window;
  return partiallyVisible
      ? ((top > 0 && top < innerHeight) ||
          (bottom > 0 && bottom < innerHeight)) &&
      ((left > 0 && left < innerWidth) || (right > 0 && right < innerWidth))
      : top >= 0 && left >= 0 && bottom <= innerHeight && right <= innerWidth;
};

window.getComputedHeight = (element) => {
  let withPaddings = element.clientHeight;
  const elementComputedStyle = window.getComputedStyle(element, null);
  return (
      withPaddings -
      parseFloat(elementComputedStyle.paddingTop) -
      parseFloat(elementComputedStyle.paddingBottom)
  );
}

window.scrollThroughParent = (element) => {
  let previousElementSibling = element.previousElementSibling;

  if (previousElementSibling) {

    window.addEventListener('scroll', () => {
      const div1Rect = previousElementSibling.getBoundingClientRect();
      const div2Rect = element.getBoundingClientRect();
      const dataToShow = {}
      dataToShow.id = element.id;
      dataToShow.div1RectBottom = div1Rect.bottom;
      dataToShow.div2RectTop = div2Rect.top;

      // console.log("Data ", dataToShow);

      if (window.scrollY > 0 && (div1Rect.bottom === 0
          || !(div1Rect.bottom < div2Rect.top))) {
        // console.log("Scrolling ", dataToShow.id);

        let header = document.getElementsByTagName('header')[0]?.offsetHeight
            ?? 0;
        const scrollY = window.scrollY + header;
        const parentContainer = element.parentNode;
        const parentTop = parentContainer.offsetTop;
        const parentHeight = getComputedHeight(parentContainer);
        const maxScroll = parentHeight - element.offsetHeight;
        let scrollYMinusParentTop = scrollY - parentTop;
        const newTop = Math.max(0, Math.min(maxScroll, scrollYMinusParentTop));
        element.style.top = newTop + `px`;
      }
    });
  }

}

window.Alpine = Alpine

Alpine.plugin(focus)
Alpine.start()
